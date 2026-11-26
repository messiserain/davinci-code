package davinci.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import davinci.common.Phase;
import davinci.common.Tile;

/**
 * GameState 규칙 시험. 테스트 프레임워크 없이 main으로 돌린다.
 * 실패가 하나라도 있으면 종료 코드 1로 끝난다.
 */
public final class GameStateTest {

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    private static final int A = 3;
    private static final int B = 5;
    private static final int C = 7;

    public static void main(String[] args) {
        run("정렬: 숫자 오름차순, 같으면 검정 먼저", GameStateTest::sorting);
        run("시작: 2인 4개씩, 정렬, 더미 16개", GameStateTest::startTwo);
        run("시작: 4인 3개씩, 24개가 모두 다름", GameStateTest::startFour);
        run("정보 은닉: 남의 숨은 타일과 뽑은 타일은 색만", GameStateTest::hiding);
        run("관전자는 공개된 타일만 숫자를 봄", GameStateTest::spectator);
        run("차례·단계가 틀리면 거절, 상태는 그대로", GameStateTest::rejects);
        run("맞힘 → 계속 → 멈춤: 숨긴 채 정렬 자리에 넣음", GameStateTest::correctThenStop);
        run("틀림: 뽑은 타일을 공개해서 넣고 차례 넘김", GameStateTest::wrongPlacesRevealed);
        run("잘못된 추리 대상·숫자 거절", GameStateTest::badGuessTargets);
        run("빈 더미: 차례가 GUESS로 시작, 틀리면 내 타일 공개", GameStateTest::emptyPiles);
        run("빈 더미에서 뽑기 거절", GameStateTest::drawFromEmptyPile);
        run("2인: 모두 공개되면 탈락하고 게임 끝", GameStateTest::twoPlayerGameOver);
        run("3인: 탈락자는 차례에서 빠지고 순위는 늦게 탈락한 순", GameStateTest::threePlayerRanks);
        run("기권: 자기 차례면 다음 사람으로", GameStateTest::forfeitOnTurn);
        run("기권: 2인이면 바로 게임 끝", GameStateTest::forfeitEndsGame);
        run("무작위 게임 1000판이 규칙을 어기지 않고 끝남", GameStateTest::randomGames);

        System.out.println();
        System.out.println("통과 " + passed + "개, 실패 " + failures.size() + "개");
        for (String f : failures) System.out.println("  실패: " + f);
        if (!failures.isEmpty()) System.exit(1);
    }

    // ---- 시험 ----

    private static void sorting() {
        List<Tile> tiles = new ArrayList<>(List.of(t("W5"), t("B6"), t("B5"), t("W0"), t("B11")));
        java.util.Collections.sort(tiles);
        check("B5 앞에 W0", tiles.toString(), "[W0, B5, W5, B6, B11]");
    }

    private static void startTwo() {
        GameState g = GameState.start(List.of(A, B), new Random(1));
        GameState.StateView v = g.stateFor(A);
        check("단계", v.phase(), Phase.DRAW);
        check("첫 차례", v.turnId(), A);
        check("더미 합", v.blackLeft() + v.whiteLeft(), 16);
        for (GameState.SeatView s : v.seats()) {
            check("타일 수", s.tiles().split("-").length, 4);
        }
        List<Tile> mine = parseOwn(v.seats().get(0).tiles());
        List<Tile> sorted = new ArrayList<>(mine);
        java.util.Collections.sort(sorted);
        check("정렬됨", mine.toString(), sorted.toString());
    }

    private static void startFour() {
        GameState g = GameState.start(List.of(1, 2, 3, 4), new Random(2));
        GameState.StateView v = g.stateFor(-1);
        check("더미 합", v.blackLeft() + v.whiteLeft(), 12);
        Set<String> seen = new HashSet<>();
        int count = 0;
        for (int id : List.of(1, 2, 3, 4)) {
            String tiles = g.stateFor(id).seats().get(id - 1).tiles();
            check("타일 수", tiles.split("-").length, 3);
            for (String code : tiles.split("-")) {
                seen.add(code);
                count++;
            }
        }
        check("겹치는 타일 없음", seen.size(), count);
    }

    private static void hiding() throws GameException {
        GameState g = twoPlayer();
        g.draw(A, Tile.Color.WHITE);
        GameState.StateView forA = g.stateFor(A);
        GameState.StateView forB = g.stateFor(B);
        check("A가 보는 자기 줄", forA.seats().get(0).tiles(), "B0-W3-B7-B9");
        check("A가 보는 B 줄", forA.seats().get(1).tiles(), "W?-B?-B?-W?");
        check("B가 보는 A 줄", forB.seats().get(0).tiles(), "B?-W?-B?-B?");
        check("A가 보는 뽑은 타일", forA.drawn(), "W8");
        check("B가 보는 뽑은 타일", forB.drawn(), "W?");

        g.guess(A, B, 2, 5); // B5 맞힘
        check("공개된 타일은 모두 봄", g.stateFor(A).seats().get(1).tiles(), "W?-B?-B5*-W?");
    }

    private static void spectator() throws GameException {
        GameState g = twoPlayer();
        g.draw(A, Tile.Color.BLACK);
        g.guess(A, B, 2, 5);
        GameState.StateView v = g.stateFor(99);
        check("A 줄", v.seats().get(0).tiles(), "B?-W?-B?-B?");
        check("B 줄", v.seats().get(1).tiles(), "W?-B?-B5*-W?");
        check("뽑은 타일", v.drawn(), "B?");
    }

    private static void rejects() throws GameException {
        GameState g = twoPlayer();
        expect(GameException.NOT_YOUR_TURN, () -> g.draw(B, Tile.Color.WHITE));
        expect(GameException.BAD_MOVE, () -> g.guess(A, B, 0, 1)); // 뽑기 전 추리
        expect(GameException.BAD_MOVE, () -> g.stop(A));
        expect(GameException.BAD_MOVE, () -> g.reveal(A, 0));
        expect(GameException.BAD_MOVE, () -> g.draw(A, null));
        check("거절 뒤 단계 그대로", g.phase(), Phase.DRAW);

        g.draw(A, Tile.Color.WHITE);
        expect(GameException.BAD_MOVE, () -> g.draw(A, Tile.Color.WHITE)); // 두 번 뽑기
        expect(GameException.NOT_YOUR_TURN, () -> g.guess(B, A, 0, 0));
        String before = g.stateFor(A).toString();
        expect(GameException.BAD_MOVE, () -> g.guess(A, B, 2, 12));
        check("거절 뒤 상태 그대로", g.stateFor(A).toString(), before);
    }

    private static void correctThenStop() throws GameException {
        GameState g = twoPlayer();
        g.draw(A, Tile.Color.WHITE); // W8
        List<GameEvent> ev = g.guess(A, B, 2, 5);
        check("이벤트", ev, List.of(new GameEvent.Guessed(A, B, 2, 5, true)));
        check("단계", g.phase(), Phase.CONTINUE);

        ev = g.guess(A, B, 0, 1); // W1 맞힘, 계속
        check("두 번째 맞힘", ev, List.of(new GameEvent.Guessed(A, B, 0, 1, true)));

        ev = g.stop(A);
        check("멈춤 이벤트", ev, List.of(new GameEvent.Placed(A, 3, false), new GameEvent.Turn(B)));
        check("A 줄에 숨긴 채 들어감", g.stateFor(A).seats().get(0).tiles(), "B0-W3-B7-W8-B9");
        check("B에게는 숨김", g.stateFor(B).seats().get(0).tiles(), "B?-W?-B?-W?-B?");
        check("뽑은 타일 비움", g.stateFor(A).drawn(), "");
        check("B 차례 뽑기", g.phase(), Phase.DRAW);
        check("차례", g.currentPlayerId(), B);
    }

    private static void wrongPlacesRevealed() throws GameException {
        GameState g = twoPlayer();
        g.draw(A, Tile.Color.BLACK); // B8
        List<GameEvent> ev = g.guess(A, B, 1, 3); // 실제 B2
        check("이벤트", ev, List.of(new GameEvent.Guessed(A, B, 1, 3, false),
                new GameEvent.Placed(A, 3, true), new GameEvent.Turn(B)));
        check("B가 보는 A 줄", g.stateFor(B).seats().get(0).tiles(), "B?-W?-B?-B8*-B?");
        check("B의 타일은 그대로 숨김", g.stateFor(A).seats().get(1).tiles(), "W?-B?-B?-W?");
    }

    private static void badGuessTargets() throws GameException {
        GameState g = twoPlayer();
        g.draw(A, Tile.Color.BLACK);
        expect(GameException.BAD_MOVE, () -> g.guess(A, A, 0, 0)); // 자기 자신
        expect(GameException.BAD_MOVE, () -> g.guess(A, 42, 0, 0)); // 없는 사람
        expect(GameException.BAD_MOVE, () -> g.guess(A, B, 4, 0)); // 범위 밖
        expect(GameException.BAD_MOVE, () -> g.guess(A, B, -1, 0));
        expect(GameException.BAD_MOVE, () -> g.guess(A, B, 0, -1));
        g.guess(A, B, 2, 5);
        expect(GameException.BAD_MOVE, () -> g.guess(A, B, 2, 5)); // 이미 공개
    }

    private static void emptyPiles() throws GameException {
        GameState g = new GameState(List.of(A, B),
                List.of(tiles("B0 W3"), tiles("W1 B2")), List.of(), List.of(), new Random(0));
        check("뽑기 없이 추리부터", g.phase(), Phase.GUESS);
        expect(GameException.BAD_MOVE, () -> g.draw(A, Tile.Color.BLACK));

        List<GameEvent> ev = g.guess(A, B, 0, 4);
        check("틀림만 알림", ev, List.of(new GameEvent.Guessed(A, B, 0, 4, false)));
        check("내 타일 공개 단계", g.phase(), Phase.REVEAL_SELF);
        expect(GameException.BAD_MOVE, () -> g.reveal(A, 2));

        ev = g.reveal(A, 1);
        check("차례 넘김", ev, List.of(new GameEvent.Turn(B)));
        check("공개됨", g.stateFor(B).seats().get(0).tiles(), "B?-W3*");
        expect(GameException.BAD_MOVE, () -> g.stop(B));

        g.guess(B, A, 0, 0); // A의 마지막 숨은 타일을 맞힘
        check("게임 끝", g.phase(), Phase.OVER);
    }

    private static void drawFromEmptyPile() throws GameException {
        GameState g = new GameState(List.of(A, B),
                List.of(tiles("B0 W3"), tiles("W1 B2")), List.of(t("B5")), List.of(), new Random(0));
        expect(GameException.BAD_MOVE, () -> g.draw(A, Tile.Color.WHITE));
        g.draw(A, Tile.Color.BLACK);
        check("검정 더미 0", g.stateFor(A).blackLeft(), 0);
        g.guess(A, B, 0, 1);
        g.stop(A);
        check("두 더미가 비면 B는 GUESS부터", g.phase(), Phase.GUESS);
    }

    private static void twoPlayerGameOver() throws GameException {
        GameState g = new GameState(List.of(A, B),
                List.of(tiles("B0 W3"), tiles("W1 B2")), List.of(t("B5")), List.of(t("W9")), new Random(0));
        g.draw(A, Tile.Color.WHITE);
        g.guess(A, B, 0, 1);
        List<GameEvent> ev = g.guess(A, B, 1, 2);
        check("이벤트 순서", ev, List.of(
                new GameEvent.Guessed(A, B, 1, 2, true),
                new GameEvent.Eliminated(B),
                new GameEvent.GameOver(List.of(new GameEvent.Rank(1, A), new GameEvent.Rank(2, B)))));
        check("단계", g.phase(), Phase.OVER);
        GameState.StateView v = g.stateFor(B);
        check("끝나면 모두 공개", v.seats().get(0).tiles(), "B0*-W3*");
        check("뽑은 타일도 공개", v.drawn(), "W9*");
        check("B 탈락", v.seats().get(1).alive(), false);
        expect(GameException.BAD_MOVE, () -> g.stop(A));
    }

    private static void threePlayerRanks() throws GameException {
        GameState g = new GameState(List.of(A, B, C),
                List.of(tiles("B0 W3"), tiles("W1"), tiles("B2 W2")), List.of(), List.of(), new Random(0));
        List<GameEvent> ev = g.guess(A, B, 0, 1); // B 탈락
        check("B 탈락", ev, List.of(new GameEvent.Guessed(A, B, 0, 1, true), new GameEvent.Eliminated(B)));
        check("계속", g.phase(), Phase.CONTINUE);
        expect(GameException.BAD_MOVE, () -> g.guess(A, B, 0, 1)); // 탈락한 사람
        ev = g.stop(A);
        check("B를 건너뜀", ev, List.of(new GameEvent.Turn(C)));

        g.guess(C, A, 0, 0);
        ev = g.guess(C, A, 1, 3);
        check("C 승리, A 2위, B 3위", ev.get(ev.size() - 1), new GameEvent.GameOver(List.of(
                new GameEvent.Rank(1, C), new GameEvent.Rank(2, A), new GameEvent.Rank(3, B))));
    }

    private static void forfeitOnTurn() throws GameException {
        GameState g = new GameState(List.of(A, B, C),
                List.of(tiles("B0 W3"), tiles("W1"), tiles("B2 W2")), List.of(t("B9")), List.of(), new Random(0));
        g.draw(A, Tile.Color.BLACK);
        List<GameEvent> ev = g.forfeit(A);
        check("다음 차례", ev, List.of(new GameEvent.Turn(B)));
        check("A 탈락, 모두 공개", g.stateFor(B).seats().get(0).tiles(), "B0*-W3*-B9*");
        check("A 탈락", g.isAlive(A), false);
        check("두 번 기권은 무시", g.forfeit(A), List.of());
        check("남의 차례에 기권", g.forfeit(C), List.of(new GameEvent.GameOver(List.of(
                new GameEvent.Rank(1, B), new GameEvent.Rank(2, C), new GameEvent.Rank(3, A)))));
    }

    private static void forfeitEndsGame() {
        GameState g = twoPlayer();
        List<GameEvent> ev = g.forfeit(B);
        check("게임 끝", ev, List.of(new GameEvent.GameOver(List.of(
                new GameEvent.Rank(1, A), new GameEvent.Rank(2, B)))));
        check("단계", g.phase(), Phase.OVER);
    }

    /** 규칙에 맞는 무작위 수만 두어서 게임이 반드시 끝나고, 은닉이 깨지지 않는지 본다. */
    private static void randomGames() throws GameException {
        Random r = new Random(12345);
        for (int game = 0; game < 1000; game++) {
            int n = 2 + r.nextInt(3);
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) ids.add(10 + i);
            GameState g = GameState.start(ids, new Random(r.nextLong()));
            int steps = 0;
            while (!g.isOver()) {
                if (++steps > 10_000) {
                    fail("게임 " + game + "이 끝나지 않음");
                    return;
                }
                int me = g.currentPlayerId();
                checkHidden(g, ids);
                switch (g.phase()) {
                    case DRAW -> {
                        GameState.StateView v = g.stateFor(me);
                        Tile.Color c = v.blackLeft() == 0 ? Tile.Color.WHITE
                                : v.whiteLeft() == 0 ? Tile.Color.BLACK
                                : r.nextBoolean() ? Tile.Color.BLACK : Tile.Color.WHITE;
                        g.draw(me, c);
                    }
                    case GUESS, CONTINUE -> {
                        if (g.phase() == Phase.CONTINUE && r.nextInt(3) == 0) {
                            g.stop(me);
                            break;
                        }
                        List<int[]> targets = new ArrayList<>();
                        for (GameState.SeatView s : g.stateFor(me).seats()) {
                            if (s.playerId() == me || !s.alive()) continue;
                            String[] codes = s.tiles().split("-");
                            for (int i = 0; i < codes.length; i++) {
                                if (codes[i].endsWith("?")) targets.add(new int[] {s.playerId(), i});
                            }
                        }
                        int[] pick = targets.get(r.nextInt(targets.size()));
                        g.guess(me, pick[0], pick[1], r.nextInt(12));
                    }
                    case REVEAL_SELF -> {
                        String[] codes = g.stateFor(me).seats().get(ids.indexOf(me)).tiles().split("-");
                        for (int i = 0; i < codes.length; i++) {
                            if (!codes[i].endsWith("*")) {
                                g.reveal(me, i);
                                break;
                            }
                        }
                    }
                    default -> throw new IllegalStateException("예상 밖 단계 " + g.phase());
                }
            }
            int alive = 0;
            for (int id : ids) {
                if (g.isAlive(id)) alive++;
            }
            if (alive != 1) fail("게임 " + game + " 생존자 " + alive + "명");
        }
    }

    /** 각 사람이 받는 STATE에 남의 숨은 타일 숫자가 없는지. */
    private static void checkHidden(GameState g, List<Integer> ids) {
        for (int viewer : ids) {
            GameState.StateView v = g.stateFor(viewer);
            for (GameState.SeatView s : v.seats()) {
                if (s.playerId() == viewer) continue;
                for (String code : s.tiles().split("-")) {
                    if (!code.endsWith("*") && !code.endsWith("?")) {
                        fail("은닉 깨짐: " + viewer + "가 " + s.playerId() + "의 " + code + "를 봄");
                    }
                }
            }
            if (viewer != v.turnId() && !v.drawn().isEmpty() && !v.drawn().endsWith("?")) {
                fail("은닉 깨짐: " + viewer + "가 뽑은 타일 " + v.drawn() + "를 봄");
            }
        }
    }

    // ---- 도우미 ----

    /** A: B0 W3 B7 B9 / B: W1 B2 B5 W10 / 더미 검정 B8, 흰 W8 (README 예시와 같은 줄). */
    private static GameState twoPlayer() {
        return new GameState(List.of(A, B),
                List.of(tiles("B9 B0 W3 B7"), tiles("W10 B5 W1 B2")),
                List.of(t("B8")), List.of(t("W8")), new Random(0));
    }

    private static Tile t(String code) {
        return new Tile(Tile.Color.fromCode(code.substring(0, 1)), Integer.parseInt(code.substring(1)));
    }

    private static List<Tile> tiles(String codes) {
        List<Tile> list = new ArrayList<>();
        for (String c : codes.split(" ")) list.add(t(c));
        return list;
    }

    private static List<Tile> parseOwn(String tiles) {
        List<Tile> list = new ArrayList<>();
        for (String c : tiles.split("-")) list.add(t(c.replace("*", "")));
        return list;
    }

    private interface Body {
        void run() throws Exception;
    }

    private interface Call {
        Object call() throws GameException;
    }

    private static void run(String name, Body body) {
        int before = failures.size();
        try {
            body.run();
        } catch (Exception e) {
            fail("예외 " + e);
        }
        if (failures.size() == before) {
            passed++;
            System.out.println("[통과] " + name);
        } else {
            System.out.println("[실패] " + name);
            for (int i = before; i < failures.size(); i++) failures.set(i, name + " - " + failures.get(i));
        }
    }

    private static void check(String what, Object actual, Object expected) {
        if (!expected.equals(actual)) fail(what + ": 기대 " + expected + ", 실제 " + actual);
    }

    private static void expect(String code, Call call) {
        try {
            call.call();
            fail(code + " 예외가 나야 하는데 성공함");
        } catch (GameException e) {
            if (!code.equals(e.code())) fail(code + " 대신 " + e.code() + ": " + e.getMessage());
        }
    }

    private static void fail(String message) {
        failures.add(message);
    }
}
