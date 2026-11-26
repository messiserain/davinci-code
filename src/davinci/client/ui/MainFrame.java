package davinci.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import davinci.client.NetClient;
import davinci.common.Msg;
import davinci.common.Proto;

/**
 * 게임 창 하나: 왼쪽 게임판, 오른쪽 채팅. 서버가 보낸 메시지를 받아 화면에 반영한다.
 * 채팅 명령어(WELCOME, ERROR, CHAT, WHISPER, SYS, USERS)는 여기서 처리하고, 나머지는 게임판(BoardPanel)으로 넘긴다.
 */
public final class MainFrame extends JFrame implements NetClient.Listener {
    private static final long serialVersionUID = 1L;
    private static final int CHAT_MAX = 200;

    private final BoardPanel board = new BoardPanel();
    private final ChatPanel chat;
    private final String host;
    private final int port;
    private NetClient net;
    private int myId;

    MainFrame(String host, int port) {
        super("다빈치 코드 온라인");
        this.host = host;
        this.port = port;
        chat = new ChatPanel(this::sendChat);

        JPanel left = new JPanel(new BorderLayout());
        left.setBackground(Color.WHITE);
        left.setBorder(BorderFactory.createLineBorder(Ui.LINE));
        left.add(board, BorderLayout.CENTER);

        // 위쪽: "< 나가기 (항복)" (화면 초안)
        JButton leave = new JButton("<  나가기 (항복)");
        leave.setFont(Ui.font(20, false));
        leave.setForeground(Ui.LINE);
        leave.setContentAreaFilled(false);
        leave.setBorderPainted(false);
        leave.setFocusPainted(false);
        leave.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        leave.setToolTipText("게임에서 나가요. 게임 중에 나가면 항복으로 처리돼요.");
        leave.addActionListener(e -> confirmLeave());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.setBackground(Color.WHITE);
        top.add(leave);

        JPanel body = new JPanel(new BorderLayout(10, 0));
        body.setBackground(Color.WHITE);
        body.add(left, BorderLayout.CENTER);
        chat.setPreferredSize(new Dimension(320, 100));
        body.add(chat, BorderLayout.EAST);

        JPanel root = new JPanel(new BorderLayout(0, 2));
        root.setBackground(Color.WHITE);
        root.setBorder(BorderFactory.createEmptyBorder(2, 8, 8, 8));
        root.add(top, BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        setContentPane(root);
        setSize(1280, 720);
        setMinimumSize(new Dimension(1000, 600));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (net != null) net.close();
            }
        });
    }

    void attach(NetClient net) {
        this.net = net;
        board.attach(net);
    }

    /** 나가기: 확인을 받은 뒤 창을 닫는다 (연결이 끊기면 서버가 다른 사람에게 퇴장을 알린다). */
    private void confirmLeave() {
        int r = JOptionPane.showConfirmDialog(this, "게임에서 나갈까요?\n게임 중에 나가면 항복으로 처리돼요.", "나가기",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (r == JOptionPane.YES_OPTION) dispose();
    }

    /**
     * 입력창에서 Enter를 누르면 온다. "/w 닉네임 내용"이면 귓속말, 아니면 모두에게 채팅.
     * 보내기 전에 탭·줄바꿈을 공백으로 바꾼다. 탭이 섞인 채로 보내면 서버에 도착한 순간 값이 둘로 나뉜다.
     */
    void sendChat(String text) {
        if (net == null) return;
        if (text.equals("/w") || text.startsWith("/w ")) {
            String rest = text.substring(2).trim();
            int sp = rest.indexOf(' ');
            if (sp < 0) {
                chat.addWarning("귓속말은 /w 닉네임 내용 으로 보내요.");
                return;
            }
            net.send(Proto.WHISPER, rest.substring(0, sp), Proto.clean(rest.substring(sp + 1), CHAT_MAX));
            return;
        }
        net.send(Proto.CHAT, Proto.clean(text, CHAT_MAX));
    }

    @Override
    public void onMessage(Msg m) {
        switch (m.cmd) {
            case Proto.WELCOME:
                myId = m.i(0, 0);
                board.setMyName(m.s(1));
                setTitle("다빈치 코드 온라인 - " + m.s(1));
                chat.addSystem("서버에 접속했어요. (내 ID " + myId + ")");
                chat.addSystem("귓속말: /w 닉네임 내용 (위 접속자 이름을 눌러도 돼요)");
                chat.setInputEnabled(true);
                break;
            case Proto.ERROR:
                if (myId == 0) {
                    // 닉네임 등록 실패: 이유를 보여 주고 접속 창을 다시 띄운다
                    JOptionPane.showMessageDialog(this, m.s(1), "접속할 수 없어요", JOptionPane.WARNING_MESSAGE);
                    dispose();
                    ClientMain.ask(host, port);
                } else {
                    chat.addWarning(m.s(1));
                }
                break;
            case Proto.CHAT:
                chat.addChat(m.s(1), m.s(2), m.i(0, 0) == myId);
                break;
            case Proto.WHISPER:
                chat.addWhisper(m.s(1), m.s(2), m.s(3), m.i(0, 0) == myId);
                break;
            case Proto.SYS:
                chat.addSystem(m.s(0));
                break;
            case Proto.USERS: {
                chat.setUsers(m.list(0), myId);
                List<String> others = new ArrayList<>(); // 나를 뺀 접속자 → 게임판이 2·3·4인 화면을 고른다
                for (String[] u : m.list(0)) if (Integer.parseInt(u[0]) != myId) others.add(u[1]);
                board.setPlayers(others);
                break;
            }
            default:
                board.onGameMessage(m); // 채팅이 아닌 메시지는 게임판으로
        }
    }

    @Override
    public void onClosed(String reason) {
        chat.addWarning("서버와 연결이 끊겼어요.");
        chat.setInputEnabled(false);
        chat.setDisconnected();
    }
}
