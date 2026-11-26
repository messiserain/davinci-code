package davinci.server;

import static davinci.common.Proto.encode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import davinci.common.Msg;
import davinci.common.Proto;

/**
 * 받은 메시지를 명령어별로 처리하고, 한 명에게(WriteOne) 또는 모두에게(WriteAll) 보낸다.
 * 이 클래스의 메서드는 모두 게임 로직 스레드 하나에서만 실행된다 (GameServer.post).
 *
 * 2주차: 닉네임 등록(HELLO), 채팅(CHAT), 접속자 목록(USERS), 입장·퇴장 알림(SYS).
 * 3주차: 귓속말(WHISPER), 도배 방지, 명령어를 표(handlers)로 등록해서 게임 명령어를 따로 붙일 수 있게 함.
 */
final class Hub {
    private static final int NAME_MAX = 10;
    private static final int CHAT_MAX = 200;

    /** 명령어 하나를 처리하는 방법. 닉네임을 등록한 사람(u)의 메시지만 넘어온다. */
    interface Handler {
        void handle(User u, Msg m);
    }

    private final GameServer server;
    private final Map<Integer, User> users = new LinkedHashMap<>(); // 들어온 순서를 지킨다
    private final Map<String, Handler> handlers = new HashMap<>();
    private final List<Consumer<User>> leaveListeners = new ArrayList<>();
    private int nextId = 1;
    private int floodCount = 5;    // floodMs 안에 이만큼 보냈으면
    private long floodMs = 3000;   // 다음 채팅은 거절한다 (GameServer.setFloodLimit으로 바꿀 수 있음)

    Hub(GameServer server) {
        this.server = server;
        // 채팅 담당 명령어
        on(Proto.CHAT, (u, m) -> chat(u, m.s(0)));
        on(Proto.WHISPER, (u, m) -> whisper(u, m.s(0), m.s(1)));
        on(Proto.HELLO, (u, m) -> u.send(error("ALREADY", "이미 접속해 있어요.")));
        // 게임 담당 명령어 (방·준비·타일 등)는 GameHandlers에서 등록한다
        GameHandlers.register(this);
    }

    /** 명령어 처리 방법을 등록한다. 같은 명령어를 두 번 등록하면 실수이므로 바로 알린다. */
    void on(String cmd, Handler h) {
        if (handlers.putIfAbsent(cmd, h) != null) throw new IllegalStateException("이미 등록된 명령어: " + cmd);
    }

    void setFlood(int count, long ms) {
        floodCount = count;
        floodMs = ms;
    }

    /** 누군가 나갔을 때 알려 달라고 등록한다 (게임 쪽에서 자리 정리용). */
    void onLeave(Consumer<User> l) {
        leaveListeners.add(l);
    }

    void onConnect(Connection c) {
        server.log("연결: " + c.remote);
    }

    void onMessage(Connection c, String line) {
        Msg m = Msg.parse(line);
        server.log("[받음] " + (c.user == null ? c.remote : c.user.name) + " : " + Proto.pretty(line));
        if (c.user == null) {
            // 닉네임을 등록하기 전에는 HELLO만 받는다
            if (m.cmd.equals(Proto.HELLO)) hello(c, m.s(0));
            else c.send(error("NOT_HELLO", "먼저 닉네임을 등록해 주세요."));
            return;
        }
        Handler h = handlers.get(m.cmd);
        if (h == null) c.send(error("UNKNOWN", "알 수 없는 요청이에요: " + m.cmd));
        else h.handle(c.user, m);
    }

    private void hello(Connection c, String raw) {
        String name = Proto.clean(raw, 20);
        String why = checkName(name);
        if (why != null) {
            c.send(error("BAD_NAME", why));
            return;
        }
        if (findByName(name) != null) {
            c.send(error("NAME_TAKEN", "이미 접속 중인 닉네임이에요. 다른 닉네임을 써 주세요."));
            return;
        }
        User u = new User(nextId++, name, c);
        c.user = u;
        users.put(u.id, u);
        u.send(encode(Proto.WELCOME, u.id, u.name));        // WriteOne
        broadcast(encode(Proto.SYS, name + "님이 들어왔어요.")); // WriteAll
        broadcastUsers();
        server.log("입장: " + name + " (ID " + u.id + ", 현재 " + users.size() + "명)");
    }

    private void chat(User u, String raw) {
        String text = Proto.clean(raw, CHAT_MAX); // 채팅 속 탭·줄바꿈이 구분자로 쓰이지 않게 공백으로 바꾼다
        if (text.isEmpty() || flooded(u)) return;
        broadcast(encode(Proto.CHAT, u.id, u.name, text));
    }

    /** 귓속말: 받는 사람과 보낸 사람 둘에게만 보낸다 (WriteOne을 두 번). 나머지는 보낸 사실도 모른다. */
    private void whisper(User from, String rawTarget, String raw) {
        String text = Proto.clean(raw, CHAT_MAX);
        if (text.isEmpty()) {
            from.send(error("EMPTY", "귓속말 내용을 입력해 주세요."));
            return;
        }
        User to = findByName(Proto.clean(rawTarget, 20));
        if (to == null) {
            from.send(error("NO_USER", "'" + Proto.clean(rawTarget, 20) + "'님은 지금 접속해 있지 않아요."));
            return;
        }
        if (to == from) {
            from.send(error("SELF", "자기 자신에게는 귓속말을 보낼 수 없어요."));
            return;
        }
        if (flooded(from)) return;
        String line = encode(Proto.WHISPER, from.id, from.name, to.name, text);
        to.send(line);
        from.send(line);
    }

    /** 최근 floodMs 안에 floodCount개를 이미 보냈으면 거절하고 true. 아니면 이번 것을 기록하고 false. */
    private boolean flooded(User u) {
        long now = System.currentTimeMillis();
        while (!u.recent.isEmpty() && now - u.recent.peekFirst() >= floodMs) u.recent.pollFirst(); // 오래된 기록 버리기
        if (u.recent.size() >= floodCount) {
            server.log("도배 거절: " + u.name + " (" + floodMs / 1000 + "초 안에 " + floodCount + "개 넘음)");
            u.send(error("FLOOD", "너무 빨리 보내고 있어요. 잠깐 쉬었다가 보내 주세요."));
            return true;
        }
        u.recent.addLast(now);
        return false;
    }

    void onDisconnect(Connection c) {
        User u = c.user;
        if (u == null) {
            server.log("연결 종료 (닉네임 등록 전): " + c.remote);
            return;
        }
        c.user = null;
        users.remove(u.id);
        for (Consumer<User> l : leaveListeners) l.accept(u);
        broadcast(encode(Proto.SYS, u.name + "님이 나갔어요."));
        broadcastUsers();
        server.log("퇴장: " + u.name + " (현재 " + users.size() + "명)");
    }

    User findByName(String name) {
        for (User u : users.values()) if (u.name.equalsIgnoreCase(name)) return u;
        return null;
    }

    /** 지금 접속한 사람들 (읽기 전용) */
    Collection<User> users() {
        return Collections.unmodifiableCollection(users.values());
    }

    private void broadcastUsers() {
        List<String> recs = new ArrayList<>();
        for (User u : users.values()) recs.add(Proto.record(u.id, u.name));
        broadcast(encode(Proto.USERS, Proto.list(recs)));
    }

    /** 닉네임을 등록한 모든 사람에게 보낸다 (수업 코드의 WriteAll). */
    void broadcast(String line) {
        for (User u : users.values()) u.send(line);
    }

    static String error(String code, String text) {
        return encode(Proto.ERROR, code, text);
    }

    /** 닉네임 규칙: 2~10글자, 한글·영문·숫자·_ 만. 문제가 없으면 null */
    static String checkName(String n) {
        if (n.isEmpty()) return "닉네임을 입력해 주세요.";
        if (n.length() < 2 || n.length() > NAME_MAX) return "닉네임은 2~10글자로 정해 주세요.";
        if (!n.matches("[가-힣a-zA-Z0-9_]+")) return "닉네임에는 한글, 영문, 숫자, _만 쓸 수 있어요.";
        return null;
    }
}
