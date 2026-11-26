package davinci.client.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JButton;

/** 화면 초안에서 정한 색과 글꼴, 둥근 버튼 */
final class Ui {
    private Ui() { }

    static final Color LINE = new Color(0x222222);
    static final Color TILE_BLACK = new Color(0x111111);
    static final Color ORANGE = new Color(0xE8743B);
    static final Color BUBBLE_OTHER = new Color(0xFCE3D6); // 다른 사람 말풍선
    static final Color BUBBLE_MINE = new Color(0xC6F0C9);  // 내 말풍선
    static final Color GRAY = new Color(0x777777);
    static final Color BUBBLE_WHISPER = new Color(0xE8DEFA); // 귓속말 말풍선 (3주차)
    static final Color WHISPER = new Color(0x7A4FC2);        // 귓속말 표시 글자
    static final Color WARN = new Color(0xC8463D);           // 거절·경고 안내
    static final Color GREEN = new Color(0x1AAE58);          // 남은 시간 원 (화면 초안)

    static Font font(int size, boolean bold) {
        return new Font("Malgun Gothic", bold ? Font.BOLD : Font.PLAIN, size);
    }

    static Graphics2D smooth(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    /** 주황색 둥근 버튼 (턴 마치기, 전송) */
    static final class RoundButton extends JButton {
        private static final long serialVersionUID = 1L;

        RoundButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setForeground(Color.WHITE);
            setFont(font(16, true));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            Color c = !isEnabled() ? new Color(0xF3C2A6) : getModel().isPressed() ? ORANGE.darker() : ORANGE;
            g.setColor(c);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.dispose();
            super.paintComponent(g0);
        }
    }
}
