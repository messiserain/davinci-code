package davinci.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.SwingUtilities;

import davinci.common.Msg;
import davinci.common.Proto;

/**
 * 서버와의 연결.
 *  - 읽기 스레드: readUTF로 받은 메시지를 화면 스레드(EDT)로 넘긴다. Swing 화면은 EDT에서만 고쳐야 하기 때문이다.
 *  - 쓰기 스레드(3주차): 화면 스레드는 보낼 줄을 큐에 넣기만 하고, 실제 writeUTF는 쓰기 스레드가 한다.
 *    네트워크가 느려도 버튼을 누른 화면이 멈추지 않는다. 서버의 Connection과 같은 구조.
 *  - 닫기: 소켓을 바로 닫지 않고 큐 끝에 CLOSE 신호를 넣는다. 쓰기 스레드가 앞에 쌓인 것을 다 보낸 뒤 닫는다.
 */
public final class NetClient {
    public interface Listener {
        void onMessage(Msg m);

        void onClosed(String reason);
    }

    private static final String CLOSE = new String("\u0000close"); // 쓰기 스레드를 끝내는 신호 (내용이 아니라 객체로 비교)

    private final Socket socket;
    private final DataOutputStream out;
    private final LinkedBlockingQueue<String> outbox = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    private NetClient(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    /** 서버에 접속하고 읽기·쓰기 스레드를 시작한다. 3초 안에 연결되지 않으면 예외. */
    public static NetClient connect(String host, int port, Listener listener) throws IOException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), 3000);
        NetClient c = new NetClient(s);
        Thread r = new Thread(() -> c.readLoop(listener), "client-read");
        r.setDaemon(true);
        r.start();
        Thread w = new Thread(c::writeLoop, "client-write");
        w.setDaemon(true);
        w.start();
        return c;
    }

    private void readLoop(Listener listener) {
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            while (true) {
                Msg m = Msg.parse(in.readUTF());
                SwingUtilities.invokeLater(() -> listener.onMessage(m));
            }
        } catch (IOException e) {
            if (!closed) SwingUtilities.invokeLater(() -> listener.onClosed(e.getMessage()));
            close(); // 서버가 끊었으면 쓰기 스레드도 끝낸다
        }
    }

    private void writeLoop() {
        try {
            while (true) {
                String line = outbox.take(); // 보낼 것이 생길 때까지 기다린다
                if (line == CLOSE) break;    // 앞에 넣은 것을 모두 보낸 뒤에야 여기에 온다
                out.writeUTF(line);
                if (outbox.isEmpty()) out.flush();
            }
            out.flush();
        } catch (IOException | InterruptedException e) {
            // 서버가 먼저 끊은 경우
        }
        closeSocket();
    }

    /** 보낼 줄을 큐에 넣고 바로 돌아온다 (어느 스레드에서 불러도 된다). */
    public void send(String cmd, Object... fields) {
        if (!closed) outbox.offer(Proto.encode(cmd, fields));
    }

    /** 더 보내지 않게 막고, 큐에 남은 것을 쓰기 스레드가 다 보낸 뒤 연결을 닫게 한다. */
    public void close() {
        if (closed) return;
        closed = true;
        outbox.offer(CLOSE);
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException ignore) {
            // 이미 닫힘
        }
    }
}
