package davinci.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 클라이언트 1명과의 연결 (수업 코드의 UserService).
 * UserService는 스레드 하나가 읽기와 쓰기를 모두 했는데, 여기서는 두 스레드로 나눴다.
 *  - 읽기 스레드: readUTF로 받은 줄을 게임 로직 스레드에 넘긴다.
 *  - 쓰기 스레드: 보낼 줄을 큐(outbox)에서 꺼내 writeUTF로 보낸다.
 * 로직 스레드는 큐에 넣기만 하므로, 네트워크가 느린 한 명 때문에 다른 사람에게 보내는 것이 멈추지 않는다.
 */
final class Connection {
    private static final String CLOSE = new String("\u0000close"); // 쓰기 스레드를 끝내는 신호 (내용이 아니라 객체로 비교)

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>();
    private final GameServer server;
    private final Hub hub;
    private volatile boolean closed;
    final String remote;
    User user; // 닉네임 등록 전에는 null. 게임 로직 스레드에서만 읽고 쓴다

    Connection(Socket socket, GameServer server, Hub hub) throws IOException {
        this.socket = socket;
        this.server = server;
        this.hub = hub;
        this.remote = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    void start() {
        Thread reader = new Thread(this::readLoop, "read-" + remote);
        reader.setDaemon(true);
        reader.start();
        Thread writer = new Thread(this::writeLoop, "write-" + remote);
        writer.setDaemon(true);
        writer.start();
        server.post(() -> hub.onConnect(this));
    }

    private void readLoop() {
        try {
            while (true) {
                String line = in.readUTF(); // 2바이트 길이 + UTF-8: 한 메시지를 다 받을 때까지 기다린다
                server.post(() -> hub.onMessage(this, line));
            }
        } catch (IOException e) {
            // 상대가 정상 종료(EOF)하거나 갑자기 끊긴(연결 리셋) 경우 모두 여기로 온다
        }
        close();
        server.post(() -> hub.onDisconnect(this));
    }

    private void writeLoop() {
        try {
            while (true) {
                String line = outbox.take(); // 보낼 것이 생길 때까지 기다린다
                if (line == CLOSE) break;
                out.writeUTF(line);
                if (outbox.isEmpty()) out.flush(); // 쌓인 것을 다 쓴 뒤 한 번에 내보낸다
            }
            out.flush();
        } catch (IOException | InterruptedException e) {
            // 상대가 먼저 끊은 경우
        }
        closeSocket();
    }

    /** 보낼 줄을 큐에 넣는다 (게임 로직 스레드에서 부른다). */
    void send(String line) {
        if (!closed) outbox.offer(line);
    }

    void close() {
        if (!closed) {
            closed = true;
            outbox.offer(CLOSE);
        }
        closeSocket();
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignore) {
            // 이미 닫힘
        }
    }
}
