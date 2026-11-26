package davinci.server;

import java.util.ArrayDeque;

/** 닉네임을 등록한 접속자 한 명 */
final class User {
    final int id;
    final String name;
    final Connection conn;
    /** 최근에 보낸 채팅·귓속말 시각 (도배 방지용). 게임 로직 스레드에서만 쓴다 */
    final ArrayDeque<Long> recent = new ArrayDeque<>();

    User(int id, String name, Connection conn) {
        this.id = id;
        this.name = name;
        this.conn = conn;
    }

    void send(String line) {
        conn.send(line);
    }
}
