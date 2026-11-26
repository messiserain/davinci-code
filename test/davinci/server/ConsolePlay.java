package davinci.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import davinci.common.Phase;
import davinci.common.Tile;

/**
 * 네트워크와 화면 없이 콘솔에서 GameState 규칙을 직접 해 보는 실행기.
 * 한 화면에서 돌아가며 두므로, 지금 차례인 사람이 받는 STATE 그대로 보여 준다.
 *
 * <pre>
 * java -cp bin davinci.server.ConsolePlay [인원 2~4] [시드]
 * </pre>
 */
public final class ConsolePlay {

    public static void main(String[] args) throws IOException {
        int count = args.length > 0 ? Integer.parseInt(args[0]) : 2;
        Random random = args.length > 1 ? new Random(Long.parseLong(args[1])) : new Random();
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i <= count; i++) ids.add(i);

        GameState game = GameState.start(ids, random);
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        System.out.println(count + "인 게임을 시작해요. 플레이어 번호: " + ids);
        System.out.println("화면에는 지금 차례인 사람이 받는 정보만 보여요. q를 누르면 끝나요.");
        int shownTurn = -1;

        while (!game.isOver()) {
            int me = game.currentPlayerId();
            if (me != shownTurn) {
                System.out.println();
                System.out.println("────────── " + me + "번 차례 ──────────");
                shownTurn = me;
            }
            printState(game.stateFor(me), me);
            System.out.print(prompt(game.phase()) + "\n> ");
            String line = in.readLine();
            if (line == null || line.trim().equals("q")) {
                System.out.println("그만할게요.");
                return;
            }
            String[] w = line.trim().split("\\s+");
            try {
                List<GameEvent> events = switch (w[0]) {
                    case "d" -> game.draw(me, w.length > 1 ? Tile.Color.fromCode(w[1].toUpperCase()) : null);
                    case "g" -> game.guess(me, num(w, 1), num(w, 2), num(w, 3));
                    case "s" -> game.stop(me);
                    case "r" -> game.reveal(me, num(w, 1));
                    case "f" -> game.forfeit(me);
                    default -> throw new GameException("BAD_FORMAT", "모르는 명령이에요.");
                };
                for (GameEvent e : events) System.out.println("  ▶ " + describe(e));
            } catch (GameException e) {
                System.out.println("  ✖ [" + e.code() + "] " + e.getMessage());
            }
        }

        System.out.println();
        System.out.println("────────── 게임 끝 ──────────");
        printState(game.stateFor(-1), -1);
    }

    private static int num(String[] w, int i) throws GameException {
        try {
            return Integer.parseInt(w[i]);
        } catch (RuntimeException e) {
            throw new GameException("BAD_FORMAT", "숫자를 빠짐없이 적어 주세요.");
        }
    }

    private static void printState(GameState.StateView v, int me) {
        System.out.println("단계 " + v.phase() + " | 더미 검정 " + v.blackLeft() + "개, 흰 " + v.whiteLeft()
                + "개" + (v.drawn().isEmpty() ? "" : " | 뽑은 타일 " + v.drawn()));
        for (GameState.SeatView s : v.seats()) {
            StringBuilder sb = new StringBuilder();
            String[] codes = s.tiles().split("-");
            for (int i = 0; i < codes.length; i++) sb.append(" [").append(i).append(']').append(codes[i]);
            String who = s.playerId() + "번" + (s.playerId() == me ? "(나)" : "    ");
            System.out.println("  " + who + (s.alive() ? "      " : "(탈락)") + ":" + sb);
        }
    }

    private static String prompt(Phase phase) {
        return switch (phase) {
            case DRAW -> "뽑기: d b (검정) / d w (흰)";
            case GUESS -> "추리: g <상대번호> <자리> <숫자>   예) g 2 1 5";
            case CONTINUE -> "맞혔어요! 계속 추리: g <상대번호> <자리> <숫자> / 멈추기: s";
            case REVEAL_SELF -> "틀렸는데 뽑은 타일이 없어요. 내 타일 공개: r <자리>";
            default -> "";
        } + "   (기권: f, 끝내기: q)";
    }

    private static String describe(GameEvent e) {
        return switch (e) {
            case GameEvent.Guessed g -> g.guesserId() + "번이 " + g.targetId() + "번의 [" + g.index() + "]을 "
                    + g.number() + "(으)로 추리 → " + (g.correct() ? "정답!" : "오답");
            case GameEvent.Placed p -> p.playerId() + "번이 뽑은 타일을 [" + p.index() + "]에 "
                    + (p.revealed() ? "공개해서" : "숨겨서") + " 넣음";
            case GameEvent.Eliminated el -> el.playerId() + "번 탈락";
            case GameEvent.Turn t -> t.playerId() + "번 차례로 넘어감";
            case GameEvent.GameOver o -> {
                StringBuilder sb = new StringBuilder("게임 끝. 순위:");
                for (GameEvent.Rank r : o.ranks()) sb.append(' ').append(r.rank()).append("위 ").append(r.playerId()).append("번");
                yield sb.toString();
            }
        };
    }
}
