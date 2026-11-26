package davinci.client.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import davinci.client.NetClient;
import davinci.common.Msg;

/**
 * 게임판 (11주차 화면 초안을 옮긴 것).
 * 접속한 사람 수(2~4명)에 맞춰 2인·3인·4인 초안 중 하나를 그린다. 상대 줄에는 실제로 접속한 다른 사람의 닉네임이 나온다.
 * 위: 상대 줄(사람 수 - 1개), 가운데: 더미·남은 시간·안내·가져온 타일, 아래: 내 줄 + 턴 마치기.
 *
 * 타일·더미 수·남은 시간은 아직 예시 데이터다.
 * 게임 담당이 서버가 보내는 게임 상태로 이 예시 데이터(EXAMPLES)를 바꿀 예정이다.
 */
final class BoardPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** 사람 수별 예시 장면 (화면 초안과 같음) */
    private static final class Example {
        final String[][] opp;    // 상대마다 타일
        final String[] mine;     // 내 타일
        final int black, white;  // 남은 더미 수
        final boolean timer;     // 남은 시간 원 (2인 초안에는 없음)

        Example(String[][] opp, String[] mine, int black, int white, boolean timer) {
            this.opp = opp;
            this.mine = mine;
            this.black = black;
            this.white = white;
            this.timer = timer;
        }
    }

    // 표기: 'B' 검정 / 'W' 흰색 + 숫자, '?' = 숫자를 모름(뒷면), '-' = 조커, 끝의 '*' = 공개된 내 타일
    private static final String[] MINE7 = { "W0*", "B1", "B4", "W4", "W7*", "B-", "B10*" };
    private static final Example EX2 = new Example(new String[][] {
        { "B0", "W?", "B3", "W?", "W8", "B?", "W?" },
    }, MINE7, 6, 5, false);
    private static final Example EX3 = new Example(new String[][] {
        { "B0", "W?", "B3", "W?", "W8", "B?", "W?" },
        { "W2", "W?", "B5", "W?", "W?", "B11", "W11" },
    }, MINE7, 2, 2, true);
    private static final Example EX4 = new Example(new String[][] {
        { "W?", "B3", "W?", "W8", "B?" },
        { "B?", "B5", "W?", "W?", "B11" },
        { "W?", "B6", "W?", "B?", "B?" },
    }, new String[] { "B1*", "B4", "W4", "W7*", "B-" }, 2, 2, true);
    private static final int SECONDS_LEFT = 17;

    private final Ui.RoundButton stopBtn = new Ui.RoundButton("턴 마치기");
    private String myName = "나";
    private final List<String> others = new ArrayList<>(); // 나를 뺀 접속자 (들어온 순서)
    private NetClient net;

    BoardPanel() {
        setLayout(null);
        setBackground(Color.WHITE);
        stopBtn.setFont(Ui.font(21, false));
        stopBtn.setToolTipText("예시 화면이에요. 게임 진행 기능과 연결할 예정이에요.");
        add(stopBtn);
    }

    /** 게임 명령어를 보낼 때 쓰는 연결 (예: net.send("READY", 1)). 채팅과 같은 연결을 함께 쓴다. */
    void attach(NetClient net) {
        this.net = net;
    }

    /**
     * 채팅 쪽(MainFrame)이 처리하지 않는 서버 메시지가 여기로 온다 (화면 스레드에서 부름).
     * 게임 담당이 ROOM, STATE 같은 게임 메시지를 받아 게임판을 그리는 곳.
     */
    void onGameMessage(Msg m) {
        // 아직 없음 (게임 담당 구현 예정)
    }

    void setMyName(String name) {
        myName = name;
        repaint();
    }

    /** 접속자 목록(USERS)이 바뀔 때 MainFrame이 부른다. 나를 뺀 닉네임들 → 2·3·4인 화면을 고른다. */
    void setPlayers(List<String> otherNames) {
        others.clear();
        others.addAll(otherNames);
        revalidate();
        repaint();
    }

    /** 지금 그릴 사람 수 (혼자여도 2인 화면, 5명 이상이면 4인 화면) */
    int players() {
        return Math.max(2, Math.min(4, 1 + others.size()));
    }

    private Example example() {
        int n = players();
        return n == 2 ? EX2 : n == 3 ? EX3 : EX4;
    }

    private String oppName(int i) {
        return i < others.size() ? others.get(i) : "상대";
    }

    // ---------------------------------------------------------------- 배치 (초안의 비율을 창 크기에 맞춰 계산)

    /** 초안에서 잰 비율: 타일 높이는 판 높이의 12.5%, 상대 줄은 15.7%(2인은 24%), 내 줄은 18%(2인은 24%) */
    private int tileH() {
        return Math.max(52, (int) (getHeight() * 0.125));
    }

    private int oppRowH() {
        return (int) (getHeight() * (players() == 2 ? 0.24 : 0.157));
    }

    private int myRowH() {
        return Math.max(tileH() + 26, (int) (getHeight() * (players() == 2 ? 0.24 : 0.18)));
    }

    private int margin() {
        return Math.max(10, (int) (getHeight() * 0.018));
    }

    private int btnW() {
        return (int) ((getWidth() - 2 * margin()) * 0.215);
    }

    @Override
    public void doLayout() {
        int w = getWidth(), h = getHeight(), m = margin();
        int myY = h - m - myRowH(), bw = btnW();
        int bh = Math.max(40, (int) (h * 0.074));
        stopBtn.setBounds(w - m - bw, myY + Math.max(8, (int) (myRowH() * 0.13)), bw, bh);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = Ui.smooth(g0);
        int w = getWidth(), h = getHeight(), m = margin();
        int rowW = w - 2 * m, gap = (int) (h * 0.028);
        int th = tileH(), oppH = oppRowH(), myH = myRowH();
        Example ex = example();
        int nameSize = Math.max(18, Math.min(30, th * 36 / 100));

        // 상대 줄 (사람 수 - 1개를 위에 쌓는다). 타일 묶음은 줄 너비의 56.5% 지점이 가운데 (초안 비율)
        int y = m;
        for (int i = 0; i < ex.opp.length; i++) {
            box(g, m, y, rowW, oppH);
            text(g, oppName(i), nameSize, m + 24, y + oppH / 2 + nameSize / 3, Ui.LINE);
            tiles(g, ex.opp[i], m + (int) (rowW * 0.565), y + (oppH - th) / 2, th, m + 24 + 3 * nameSize, m + rowW - 16, false);
            y += oppH + gap;
        }
        int oppBottom = y - gap;

        // 내 줄 + 턴 마치기. 타일 묶음은 내 줄 너비의 61% 지점이 가운데
        int myY = h - m - myH, myW = (int) (rowW * 0.736);
        box(g, m, myY, myW, myH);
        text(g, myName + " (나)", nameSize, m + 24, myY + myH / 2 + nameSize / 3, Ui.LINE);
        int mth = Math.min(th, myH - 30);
        tiles(g, ex.mine, m + (int) (myW * 0.61), myY + (myH - mth - 18) / 2, mth, m + 24 + 5 * nameSize, m + myW - 14, true);

        // 가운데: 더미 두 개, 남은 시간(3·4인), 안내 문구, 가져온 타일
        int midY = (oppBottom + myY) / 2 - 6;
        int pw = Math.max(44, Math.min(th * 81 / 100, (int) (rowW * 0.075))), ph = Math.min(th, myY - oppBottom - 40);
        int px = m + (int) (rowW * 0.012), py = midY - ph / 2 - 8;
        tile(g, px, py, pw, ph, 'B', "", false);
        tile(g, px + pw + 14, py, pw, ph, 'W', "", false);
        centerText(g, "검정 " + ex.black, 17, px + pw / 2, py + ph + 22);
        centerText(g, "흰색 " + ex.white, 17, px + pw + 14 + pw / 2, py + ph + 22);
        int textX = px + 2 * pw + 14 + 30;
        if (ex.timer) {
            int r = Math.max(26, Math.min(40, ph * 45 / 100)), cx = textX + r;
            g.setColor(Ui.GREEN);
            g.setStroke(new BasicStroke(2.2f));
            g.drawOval(cx - r, py + ph / 2 - r, 2 * r, 2 * r);
            centerText(g, String.valueOf(SECONDS_LEFT), 22, cx, py + ph / 2 + 8);
            textX = cx + r + 26;
        }
        int boxW = (int) (rowW * 0.24);
        int boxH = players() == 2 ? Math.min(oppH, myY - oppBottom - 24) : ph + 26;
        int bx = w - m - boxW, by = py + ph / 2 - boxH / 2;
        g.setColor(Ui.LINE);
        g.setStroke(new BasicStroke(1.6f));
        g.drawRoundRect(bx, by, boxW, boxH, 34, 34);
        int fw = pw * 9 / 10, fh = Math.min(ph, boxH - 20);
        tile(g, bx + 12, by + (boxH - fh) / 2, fw, fh, 'W', "5", false);
        fitText(g, "가져온 타일", 22, bx + 12 + fw + 12, by + boxH / 2 + 8, bx + boxW - 8);
        // 안내 문구: 남은 칸에 맞게 글자 크기를 줄인다 (창을 줄여도 가져온 타일 칸과 겹치지 않게)
        fitText(g, "정답! 한 번 더 할까요?", 27, textX, py + ph / 2 + 9, bx - 14);

        g.setFont(Ui.font(11, false));
        g.setColor(Ui.GRAY);
        g.drawString("※ 예시 화면 (" + players() + "명): 타일·차례·남은 시간은 게임 진행 기능과 연결할 예정", m + 4, myY - 6);
        g.dispose();
    }

    /** 타일 줄: 묶음의 가운데를 centerX에 맞추고, left~right 안에 들어가게 타일 폭을 줄인다. 내 줄은 공개된 타일 아래에 "공개". */
    private void tiles(Graphics2D g, String[] codes, int centerX, int y, int th, int left, int right, boolean mine) {
        int n = codes.length;
        int tw = th * 81 / 100, gap = Math.max(4, tw / 16);
        int maxW = right - left;
        if (n * tw + (n - 1) * gap > maxW) {
            tw = Math.max(18, (maxW - (n - 1) * gap) / n);
        }
        int total = n * tw + (n - 1) * gap;
        int x = Math.max(left, Math.min(right - total, centerX - total / 2));
        for (int i = 0; i < n; i++) {
            String c = codes[i];
            boolean open = c.endsWith("*");
            String face = c.substring(1, open ? c.length() - 1 : c.length());
            if (face.equals("?")) face = "";
            tile(g, x + i * (tw + gap), y, tw, th, c.charAt(0), face, mine && open);
        }
    }

    private void tile(Graphics2D g, int x, int y, int w, int h, char color, String face, boolean badge) {
        boolean black = color == 'B';
        g.setColor(black ? Ui.TILE_BLACK : Color.WHITE);
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(Ui.LINE);
        g.setStroke(new BasicStroke(1.6f));
        g.drawRoundRect(x, y, w, h, 12, 12);
        if (!face.isEmpty()) {
            int size = Math.min(h * 6 / 10, face.length() > 1 ? w * 62 / 100 : w * 85 / 100);
            g.setFont(Ui.font(size, false));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(black ? Color.WHITE : Ui.LINE);
            g.drawString(face, x + (w - fm.stringWidth(face)) / 2, y + (h + fm.getAscent() - fm.getDescent()) / 2);
        }
        if (badge) { // 내 타일 중 공개된 것
            int bw = Math.max(30, w * 2 / 3), bh = 15, bx = x + (w - bw) / 2, by = y + h + 4;
            g.setColor(Color.WHITE);
            g.fillRoundRect(bx, by, bw, bh, 6, 6);
            g.setColor(Ui.LINE);
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(bx, by, bw, bh, 6, 6);
            g.setFont(Ui.font(9, false));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("공개", bx + (bw - fm.stringWidth("공개")) / 2, by + 11);
        }
    }

    private void box(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(Ui.LINE);
        g.setStroke(new BasicStroke(1.6f));
        g.drawRoundRect(x, y, w, h, 40, 40);
    }

    private void text(Graphics2D g, String s, int size, int x, int y, Color c) {
        g.setFont(Ui.font(size, false));
        g.setColor(c);
        g.drawString(s, x, y);
    }

    private void centerText(Graphics2D g, String s, int size, int cx, int y) {
        g.setFont(Ui.font(size, false));
        g.setColor(Ui.LINE);
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    /** x부터 right 안에 들어가도록 글자 크기를 줄여서 그린다 (13보다 작아져야 하면 그리지 않음). */
    private void fitText(Graphics2D g, String s, int size, int x, int y, int right) {
        int sz = size;
        Font f = Ui.font(sz, false);
        while (sz > 13 && g.getFontMetrics(f).stringWidth(s) > right - x) f = Ui.font(--sz, false);
        if (g.getFontMetrics(f).stringWidth(s) > right - x) return;
        g.setFont(f);
        g.setColor(Ui.LINE);
        g.drawString(s, x, y);
    }
}
