package davinci.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import davinci.common.Proto;

/**
 * 게임 서버. 수업 코드처럼 AcceptServer 스레드가 접속을 받고, 접속마다 Connection(읽기·쓰기 스레드)을 만든다.
 * 수업 코드와 다른 점: 받은 메시지를 각 스레드에서 바로 처리하지 않고 "게임 로직 스레드" 하나에 넘겨서 순서대로 처리한다.
 * 그래서 접속자 목록 같은 공유 데이터에 synchronized를 붙이지 않아도 두 사람의 요청이 섞이지 않는다.
 *
 *   실행: java -cp bin davinci.server.GameServer [포트]
 */
public final class GameServer {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int port;
    private final Hub hub = new Hub(this);
    private final ExecutorService logic = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "logic");
        t.setDaemon(true);
        return t;
    });
    private ServerSocket serverSocket;
    private volatile boolean running;

    public GameServer(int port) {
        this.port = port;
    }

    /** 서버를 열고 실제로 열린 포트를 돌려준다 (0을 주면 비어 있는 포트를 고른다). */
    public int start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        new AcceptServer().start();
        log("서버 시작: 포트 " + serverSocket.getLocalPort());
        return serverSocket.getLocalPort();
    }

    /** 접속 요청을 기다렸다가 클라이언트마다 Connection을 만든다. */
    private final class AcceptServer extends Thread {
        AcceptServer() {
            super("accept");
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Socket s = serverSocket.accept();
                    new Connection(s, GameServer.this, hub).start();
                } catch (IOException e) {
                    if (running) log("accept 오류: " + e.getMessage());
                }
            }
        }
    }

    /** 게임 로직 스레드에서 실행한다. 처리 중 예외가 나도 서버 전체가 멈추지 않게 잡아서 로그만 남긴다. */
    void post(Runnable r) {
        logic.execute(() -> {
            try {
                r.run();
            } catch (RuntimeException e) {
                log("처리 중 오류: " + e);
            }
        });
    }

    /** 도배 기준: ms 안에 count개까지 (기본 3초에 5개). 게임 로직 스레드에서 바꾼다. */
    public void setFloodLimit(int count, long ms) {
        post(() -> hub.setFlood(count, ms));
    }

    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignore) {
            // 이미 닫힘
        }
        logic.shutdown();
        log("서버 종료");
    }

    public synchronized void log(String text) {
        System.out.println(LocalTime.now().format(TIME) + "  " + text);
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Proto.PORT;
        new GameServer(port).start();
    }
}
