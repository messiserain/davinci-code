package davinci.client.ui;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import davinci.server.GameServer;

/**
 * 실행 화면 확인용: 서버를 열고 창 세 개(재윤, 제윤, 민수)로 접속해 채팅·귓속말을 주고받은 뒤 창을 그림 파일로 저장한다.
 *   실행: java -cp bin davinci.client.Shots 저장폴더
 */
public final class Shots {
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "shots");
        dir.mkdirs();
        GameServer server = new GameServer(0);
        int port = server.start();
        String[] names = { "재윤", "제윤", "민수" };
        MainFrame[] f = new MainFrame[3];
        for (int i = 0; i < 3; i++) {
            int k = i;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    if (k == 0) UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                    f[k] = ClientMain.open("127.0.0.1", port, names[k]);
                    f[k].setLocation(20 + 60 * k, 20 + 40 * k);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            Thread.sleep(700);
        }
        say(f[0], "다들 들어왔네요!");
        say(f[1], "안녕하세요");
        say(f[2], "저도 왔어요");
        say(f[0], "/w 제윤 게임 담당 쪽은 잘 되고 있어요?");
        say(f[1], "/w 재윤 네! 방 만들기부터 하고 있어요");
        Thread.sleep(800);
        for (int i = 0; i < 3; i++) shot(f[i], new File(dir, "0" + (i + 1) + "_" + names[i] + "_화면.png"));
        server.stop();
        System.out.println("스크린샷 저장: " + dir.getAbsolutePath());
        System.exit(0);
    }

    private static void say(MainFrame f, String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> f.sendChat(text));
        Thread.sleep(400);
    }

    private static void shot(MainFrame f, File file) throws Exception {
        BufferedImage[] img = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            JRootPane rp = f.getRootPane();
            img[0] = new BufferedImage(rp.getWidth(), rp.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img[0].createGraphics();
            rp.paint(g);
            g.dispose();
        });
        ImageIO.write(img[0], "png", file);
        System.out.println("  찍음: " + file.getName());
    }
}
