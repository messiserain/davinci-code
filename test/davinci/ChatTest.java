package davinci;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import davinci.client.NetClient;
import davinci.common.Msg;
import davinci.common.Proto;
import davinci.server.GameServer;

/**
 * 화면 없이 소켓으로 서버에 붙어서 검사한다.
 * 2주차: 닉네임 등록·거절, 채팅 전달, 입장·퇴장, 순서.  3주차: 귓속말, 도배 방지, 클라이언트 보내기 스레드.
 *   실행: java -cp bin davinci.ChatTest
 */
public final class ChatTest {
    private static int pass, fail;

    static void check(boolean ok, String what) {
        if (ok) pass++;
        else fail++;
        System.out.println((ok ? "  [통과] " : "  [실패] ") + what);
    }

    /** 테스트용 클라이언트: 받은 메시지를 큐에 쌓아 두고, 원하는 명령어가 올 때까지 기다린다. */
    static final class Raw {
        final Socket s;
        final DataOutputStream out;
        final LinkedBlockingQueue<Msg> inbox = new LinkedBlockingQueue<>();

        Raw(int port) throws IOException {
            s = new Socket("127.0.0.1", port);
            out = new DataOutputStream(s.getOutputStream());
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            Thread t = new Thread(() -> {
                try {
                    while (true) inbox.add(Msg.parse(in.readUTF()));
                } catch (IOException e) {
                    inbox.add(Msg.parse("__CLOSED"));
                }
            });
            t.setDaemon(true);
            t.start();
        }

        void send(String cmd, Object... f) throws IOException {
            out.writeUTF(Proto.encode(cmd, f));
            out.flush();
        }

        Msg expect(String cmd) throws InterruptedException {
            long end = System.currentTimeMillis() + 5000;
            while (System.currentTimeMillis() < end) {
                Msg m = inbox.poll(100, TimeUnit.MILLISECONDS);
                if (m != null && m.cmd.equals(cmd)) return m;
            }
            return Msg.parse("__TIMEOUT");
        }

        /** ms 동안 cmd가 오지 않으면 true (오면 안 되는 사람에게 안 갔는지 확인) */
        boolean none(String cmd, long ms) throws InterruptedException {
            long end = System.currentTimeMillis() + ms;
            while (System.currentTimeMillis() < end) {
                Msg m = inbox.poll(50, TimeUnit.MILLISECONDS);
                if (m != null && m.cmd.equals(cmd)) return false;
            }
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        GameServer server = new GameServer(0); // 비어 있는 포트
        int port = server.start();

        System.out.println("- 닉네임 등록");
        Raw a = new Raw(port);
        a.send(Proto.HELLO, "재윤");
        Msg wa = a.expect(Proto.WELCOME);
        check(wa.i(0, 0) == 1 && wa.s(1).equals("재윤"), "재윤 접속 → WELCOME 1");
        check(a.expect(Proto.USERS).list(0).size() == 1, "접속자 목록 1명");
        Raw b = new Raw(port);
        b.send(Proto.HELLO, "제윤");
        check(b.expect(Proto.WELCOME).i(0, 0) == 2, "제윤 접속 → WELCOME 2");
        check(a.expect(Proto.SYS).s(0).equals("제윤님이 들어왔어요."), "먼저 들어온 재윤에게 입장 알림");
        check(a.expect(Proto.USERS).list(0).size() == 2, "재윤의 접속자 목록이 2명으로 바뀜");

        Raw dup = new Raw(port);
        dup.send(Proto.HELLO, "재윤");
        Msg e1 = dup.expect(Proto.ERROR);
        check(e1.s(0).equals("NAME_TAKEN"), "같은 닉네임은 거절 (" + e1.s(0) + ")");
        Raw bad = new Raw(port);
        bad.send(Proto.HELLO, "a");
        check(bad.expect(Proto.ERROR).s(0).equals("BAD_NAME"), "1글자 닉네임은 거절");
        Raw early = new Raw(port);
        early.send(Proto.CHAT, "닉네임 없이 채팅");
        check(early.expect(Proto.ERROR).s(0).equals("NOT_HELLO"), "닉네임 등록 전 채팅은 거절");

        System.out.println("- 채팅");
        a.send(Proto.CHAT, "안녕하세요");
        Msg cb = b.expect(Proto.CHAT);
        check(cb.i(0, 0) == 1 && cb.s(1).equals("재윤") && cb.s(2).equals("안녕하세요"), "재윤의 채팅이 제윤에게 감");
        check(a.expect(Proto.CHAT).s(2).equals("안녕하세요"), "보낸 사람에게도 다시 옴 (내 말풍선으로 표시)");
        b.send(Proto.CHAT, "반갑습니다 😀");
        check(a.expect(Proto.CHAT).s(2).equals("반갑습니다 😀"), "한글·이모지가 깨지지 않음");
        b.expect(Proto.CHAT); // 제윤에게도 자기 채팅이 돌아오므로 큐에서 꺼내 둔다
        a.send(Proto.CHAT, "탭\t끼우기"); // 화면을 거치지 않고 탭을 그대로 넣어 보낸다
        Msg ct = b.expect(Proto.CHAT);
        check(ct.size() == 3 && ct.s(2).equals("탭"), "서버는 첫 번째 값만 채팅으로 씀: 탭 뒤 글자는 버려지고 가짜 값이 붙지 않음 (값 " + ct.size() + "개, 내용 '" + ct.s(2) + "')");
        check(Proto.clean("탭\t끼우기", 200).equals("탭 끼우기"), "그래서 클라이언트가 보내기 전에 탭을 공백으로 바꿈");
        a.send(Proto.CHAT, "   ");
        a.send(Proto.CHAT, "빈 채팅 다음");
        check(b.expect(Proto.CHAT).s(2).equals("빈 채팅 다음"), "공백뿐인 채팅은 보내지 않음");

        System.out.println("- 순서");
        server.setFloodLimit(1000, 3000); // 순서 검사는 연결 하나의 순서를 보려는 것이라 도배 기준을 넉넉하게
        for (int i = 1; i <= 50; i++) a.send(Proto.CHAT, "순서 " + i);
        int inOrder = 0;
        for (int i = 1; i <= 50; i++) {
            if (!b.expect(Proto.CHAT).s(2).equals("순서 " + i)) break; // 처음 어긋난 곳에서 멈춘다
            inOrder++;
        }
        check(inOrder == 50, "재윤이 연달아 보낸 50개가 제윤에게 같은 순서로 도착 (순서대로 받은 개수 " + inOrder + ")");

        System.out.println("- 나가기");
        b.s.close(); // 창을 닫거나 프로그램이 꺼진 경우와 같다
        check(a.expect(Proto.SYS).s(0).equals("제윤님이 나갔어요."), "제윤이 끊기면 재윤에게 퇴장 알림");
        check(a.expect(Proto.USERS).list(0).size() == 1, "접속자 목록이 1명으로 바뀜");
        Raw b2 = new Raw(port);
        b2.send(Proto.HELLO, "제윤");
        Msg w2 = b2.expect(Proto.WELCOME);
        check(w2.cmd.equals(Proto.WELCOME) && w2.i(0, 0) == 3, "나간 닉네임은 다시 쓸 수 있음 (새 ID " + w2.i(0, 0) + ", 거절된 접속은 ID를 받지 않음)");

        System.out.println("- 귓속말 (3주차)");
        Raw c = new Raw(port);
        c.send(Proto.HELLO, "민수");
        c.expect(Proto.WELCOME);
        a.send(Proto.WHISPER, "제윤", "비밀 이야기");
        Msg wb = b2.expect(Proto.WHISPER);
        check(wb.i(0, 0) == 1 && wb.s(1).equals("재윤") && wb.s(2).equals("제윤") && wb.s(3).equals("비밀 이야기"), "재윤 → 제윤 귓속말이 제윤에게 감");
        check(a.expect(Proto.WHISPER).s(3).equals("비밀 이야기"), "보낸 재윤에게도 표시용으로 돌아옴");
        check(c.none(Proto.WHISPER, 500), "민수에게는 가지 않음");
        a.send(Proto.WHISPER, "없는사람", "안녕");
        check(a.expect(Proto.ERROR).s(0).equals("NO_USER"), "접속하지 않은 닉네임에게는 거절 (NO_USER)");
        a.send(Proto.WHISPER, "재윤", "나에게");
        check(a.expect(Proto.ERROR).s(0).equals("SELF"), "자기 자신에게는 거절 (SELF)");
        a.send(Proto.WHISPER, "제윤", "   ");
        check(a.expect(Proto.ERROR).s(0).equals("EMPTY"), "내용이 없으면 거절 (EMPTY)");

        System.out.println("- 도배 방지 (3주차)");
        server.setFloodLimit(5, 3000); // 기본값으로 되돌림
        for (int i = 1; i <= 6; i++) c.send(Proto.CHAT, "도배 " + i);
        int delivered = 0;
        for (int i = 1; i <= 5; i++) if (b2.expect(Proto.CHAT).s(2).equals("도배 " + i)) delivered++;
        check(delivered == 5, "3초 안에 5개까지는 전달");
        check(c.expect(Proto.ERROR).s(0).equals("FLOOD"), "6번째는 보낸 사람에게 거절 (FLOOD)");
        check(b2.none(Proto.CHAT, 500), "6번째는 다른 사람에게 가지 않음");
        Thread.sleep(3100);
        c.send(Proto.CHAT, "잠깐 쉬고 다시");
        check(b2.expect(Proto.CHAT).s(2).equals("잠깐 쉬고 다시"), "3초 쉬면 다시 보낼 수 있음");

        System.out.println("- 클라이언트 보내기 스레드 (3주차)");
        LinkedBlockingQueue<Msg> got = new LinkedBlockingQueue<>();
        NetClient nc = NetClient.connect("127.0.0.1", port, new NetClient.Listener() {
            @Override
            public void onMessage(Msg m) {
                got.add(m);
            }

            @Override
            public void onClosed(String reason) {
            }
        });
        nc.send(Proto.HELLO, "지수");
        Msg wj = null;
        for (long end = System.currentTimeMillis() + 5000; wj == null && System.currentTimeMillis() < end; ) {
            Msg m = got.poll(100, TimeUnit.MILLISECONDS);
            if (m != null && m.cmd.equals(Proto.WELCOME)) wj = m;
        }
        check(wj != null, "NetClient로 접속 (보내기는 쓰기 스레드가 함)");
        nc.send(Proto.CHAT, "마지막 인사");
        nc.close(); // 보내자마자 창을 닫은 경우
        check(b2.expect(Proto.CHAT).s(2).equals("마지막 인사"), "보내자마자 닫아도 마지막 채팅이 전달됨");

        server.stop();
        System.out.println();
        System.out.println("ChatTest: 통과 " + pass + ", 실패 " + fail);
        System.exit(fail > 0 ? 1 : 0);
    }
}
