package davinci.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Cursor;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * 오른쪽 채팅 창: 다른 사람 말은 왼쪽(주황), 내 말은 오른쪽(초록), 귓속말은 보라, 입장·퇴장 안내는 가운데.
 * 위쪽 접속자 목록에서 다른 사람 이름을 누르면 입력창에 "/w 이름 "이 들어간다 (3주차).
 */
final class ChatPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JPanel list = new JPanel();
    private final JScrollPane scroll;
    private final JTextField input = new JTextField();
    private final Ui.RoundButton send = new Ui.RoundButton("전송");
    private final Consumer<String> onSend;
    private final JPanel userStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));

    ChatPanel(Consumer<String> onSend) {
        super(new BorderLayout());
        this.onSend = onSend;
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createLineBorder(Ui.LINE));

        JLabel title = new JLabel("채팅");
        title.setFont(Ui.font(20, false));
        title.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.LINE), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        userStrip.setBackground(new Color(0xF7F7F7));
        userStrip.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xDDDDDD)));
        JPanel north = new JPanel(new BorderLayout());
        north.add(title, BorderLayout.NORTH);
        north.add(userStrip, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        setDisconnected();

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(Color.WHITE);
        JPanel holder = new JPanel(new BorderLayout()); // 메시지가 적을 때 위쪽부터 쌓이게
        holder.setBackground(Color.WHITE);
        holder.add(list, BorderLayout.NORTH);
        scroll = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 0));
        bottom.setBackground(Color.WHITE);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        input.setFont(Ui.font(14, false));
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Ui.ORANGE), BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        send.setPreferredSize(new Dimension(64, 36));
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        ActionListener submit = e -> submit();
        input.addActionListener(submit); // Enter
        send.addActionListener(submit);
        setInputEnabled(false);
    }

    void setInputEnabled(boolean on) {
        input.setEnabled(on);
        send.setEnabled(on);
        if (on) input.requestFocusInWindow();
    }

    private void submit() {
        String text = input.getText().trim();
        if (text.isEmpty()) return;
        onSend.accept(text);
        input.setText("");
    }

    void addChat(String name, String text, boolean mine) {
        JPanel row = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 8, 3));
        row.setOpaque(false);
        Bubble b = new Bubble(text, mine ? Ui.BUBBLE_MINE : Ui.BUBBLE_OTHER);
        if (mine) {
            row.add(b);
        } else {
            JPanel col = new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            JLabel who = new JLabel(name);
            who.setFont(Ui.font(11, false));
            who.setForeground(Ui.GRAY);
            col.add(who);
            col.add(Box.createVerticalStrut(2));
            col.add(b);
            row.add(col);
        }
        addRow(row);
    }

    /** 귓속말: 내가 보낸 것은 오른쪽 "귓속말 → 받는 사람", 받은 것은 왼쪽 "보낸 사람 → 나" */
    void addWhisper(String from, String to, String text, boolean mine) {
        JPanel row = new JPanel(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 8, 3));
        row.setOpaque(false);
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        JLabel who = new JLabel(mine ? "귓속말 → " + to : from + " → 나 (귓속말)");
        who.setFont(Ui.font(11, true));
        who.setForeground(Ui.WHISPER);
        Bubble b = new Bubble(text, Ui.BUBBLE_WHISPER);
        float align = mine ? RIGHT_ALIGNMENT : LEFT_ALIGNMENT;
        who.setAlignmentX(align);
        b.setAlignmentX(align);
        col.add(who);
        col.add(Box.createVerticalStrut(2));
        col.add(b);
        row.add(col);
        addRow(row);
    }

    void addSystem(String text) {
        addNotice(text, Ui.GRAY);
    }

    /** 서버가 거절한 이유 등 (빨간 글자) */
    void addWarning(String text) {
        addNotice(text, Ui.WARN);
    }

    private void addNotice(String text, Color color) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        row.setOpaque(false);
        JLabel l = new JLabel(text);
        l.setFont(Ui.font(12, false));
        l.setForeground(color);
        row.add(l);
        addRow(row);
    }

    /** 접속자 목록 (USERS 항목: ID, 닉네임). 다른 사람 이름을 누르면 귓속말 준비 */
    void setUsers(List<String[]> users, int myId) {
        userStrip.removeAll();
        JLabel count = new JLabel("접속자 " + users.size() + "명");
        count.setFont(Ui.font(12, true));
        count.setForeground(Ui.GRAY);
        userStrip.add(count);
        for (String[] u : users) {
            boolean me = Integer.parseInt(u[0]) == myId;
            JLabel chip = new JLabel(me ? u[1] + " (나)" : u[1]);
            chip.setFont(Ui.font(12, false));
            chip.setForeground(me ? Ui.GRAY : Ui.WHISPER);
            chip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(me ? new Color(0xCCCCCC) : Ui.WHISPER, 1, true),
                    BorderFactory.createEmptyBorder(1, 6, 1, 6)));
            if (!me) {
                chip.setToolTipText(u[1] + "님에게 귓속말");
                chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                chip.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        startWhisper(u[1]);
                    }
                });
            }
            userStrip.add(chip);
        }
        userStrip.revalidate();
        userStrip.repaint();
    }

    void setDisconnected() {
        userStrip.removeAll();
        JLabel l = new JLabel("접속자 없음");
        l.setFont(Ui.font(12, false));
        l.setForeground(Ui.GRAY);
        userStrip.add(l);
        userStrip.revalidate();
        userStrip.repaint();
    }

    /** 입력창에 "/w 이름 "을 넣고 커서를 맨 뒤로 */
    void startWhisper(String name) {
        if (!input.isEnabled()) return;
        input.setText("/w " + name + " ");
        input.requestFocusInWindow();
        input.setCaretPosition(input.getText().length());
    }

    private void addRow(JComponent row) {
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        list.add(row);
        list.revalidate();
        list.repaint();
        SwingUtilities.invokeLater(() -> { // 화면 배치가 끝난 뒤 맨 아래로
            JScrollBar bar = scroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    /** 둥근 말풍선. 글이 길면 170px 폭에서 줄을 바꾼다. */
    private static final class Bubble extends JLabel {
        private static final long serialVersionUID = 1L;
        private final Color fill;

        Bubble(String text, Color fill) {
            this.fill = fill;
            setFont(Ui.font(14, false));
            String esc = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            boolean wide = getFontMetrics(getFont()).stringWidth(text) > 170;
            setText(wide ? "<html><body style='width:170px'>" + esc + "</body></html>" : "<html>" + esc + "</html>");
            setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Ui.smooth(g0);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g.dispose();
            super.paintComponent(g0);
        }
    }
}
