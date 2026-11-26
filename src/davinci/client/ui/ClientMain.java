package davinci.client.ui;

import java.awt.GridLayout;
import java.io.IOException;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import davinci.client.NetClient;
import davinci.common.Proto;

/**
 * 클라이언트 시작점: 서버 주소·포트·닉네임을 묻고 접속한 뒤 게임 창을 띄운다.
 *   실행: java -cp bin davinci.client.ClientMain
 */
public final class ClientMain {
    private ClientMain() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignore) {
                // 기본 모양 사용
            }
            ask("127.0.0.1", Proto.PORT);
        });
    }

    /** 접속 정보를 묻는다. 취소하면 끝. */
    static void ask(String host, int port) {
        JTextField hostField = new JTextField(host);
        JTextField portField = new JTextField(String.valueOf(port));
        JTextField nameField = new JTextField();
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 4));
        p.add(new JLabel("서버 주소"));
        p.add(hostField);
        p.add(new JLabel("포트"));
        p.add(portField);
        p.add(new JLabel("닉네임 (2~10글자)"));
        p.add(nameField);
        int r = JOptionPane.showConfirmDialog(null, p, "다빈치 코드 온라인 - 접속", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (r != JOptionPane.OK_OPTION) return;
        String h = hostField.getText().trim();
        int pt;
        try {
            pt = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(null, "포트는 숫자로 입력해 주세요.");
            ask(h, port);
            return;
        }
        try {
            open(h, pt, nameField.getText().trim());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, h + ":" + pt + " 에 접속할 수 없어요.\n" + e.getMessage(), "접속 실패", JOptionPane.ERROR_MESSAGE);
            ask(h, pt);
        }
    }

    /** 접속해서 닉네임을 보내고 게임 창을 띄운다 (화면 스레드에서 부른다). */
    static MainFrame open(String host, int port, String name) throws IOException {
        MainFrame f = new MainFrame(host, port);
        NetClient net = NetClient.connect(host, port, f);
        f.attach(net);
        net.send(Proto.HELLO, name);
        f.setVisible(true);
        return f;
    }
}
