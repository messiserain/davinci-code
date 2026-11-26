package davinci.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import davinci.common.Phase;
import davinci.common.Tile;

/**
 * 방 하나의 게임 상태와 규칙 판정. 소켓을 모르고, 요청을 받아 상태를 바꾸고 알릴 일을 돌려주기만 한다.
 *
 * <p>스레드 안전하지 않다. 서버의 게임 로직 스레드 하나에서만 부른다.
 *
 * <p>처리하는 쪽이 할 일: 요청 메서드가 돌려준 {@link GameEvent}를 차례대로 방 안 모두에게 보내고,
 * 이어서 방 안 한 사람씩 {@link #stateFor(int)}로 만든 {@code STATE}를 보낸다.
 * 단, {@link GameEvent.GameOver}는 마지막 {@code STATE}를 보낸 뒤에 보낸다.
 * 요청이 규칙에 어긋나면 {@link GameException}이 나고 상태는 그대로다.
 */
public final class GameState {

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;

    private static final class Player {
        final int id;
        final List<Tile> hand = new ArrayList<>();
        boolean alive = true;

        Player(int id) {
            this.id = id;
        }

        boolean allRevealed() {
            for (Tile t : hand) {
                if (!t.isRevealed()) return false;
            }
            return true;
        }
    }

    /** {@code STATE} 메시지 한 통에 들어갈 값. 받는 사람에 맞춰 숨길 숫자는 이미 숨겨져 있다. */
    public record StateView(Phase phase, int turnId, int blackLeft, int whiteLeft, String drawn,
            List<SeatView> seats) {
    }

    /** {@code STATE}의 자리 하나. tiles는 타일 표기를 {@code -}로 이은 것이다. */
    public record SeatView(int playerId, boolean alive, String tiles) {
    }

    private final List<Player> players = new ArrayList<>();
    private final List<Tile> blackPile;
    private final List<Tile> whitePile;
    private final Random random;
    /** 탈락한 순서대로 쌓은 플레이어 ID. */
    private final List<Integer> outOrder = new ArrayList<>();

    private Phase phase;
    private int turn;
    private Tile drawn;

    /** 인원에 따른 시작 타일 수: 2~3인은 4개, 4인은 3개. */
    public static int startingTiles(int playerCount) {
        return playerCount >= 4 ? 3 : 4;
    }

    /**
     * 타일 24개를 섞어 나눠 주고 정렬해서 새 게임을 만든다. 첫 차례는 목록의 첫 사람이다.
     *
     * @param playerIds 차례 순서대로 놓은 플레이어 ID (2~4명, 중복 없음)
     */
    public static GameState start(List<Integer> playerIds, Random random) {
        checkPlayers(playerIds);
        List<Tile> all = new ArrayList<>();
        for (Tile.Color c : Tile.Color.values()) {
            for (int n = Tile.MIN_NUMBER; n <= Tile.MAX_NUMBER; n++) {
                all.add(new Tile(c, n));
            }
        }
        Collections.shuffle(all, random);

        int each = startingTiles(playerIds.size());
        List<List<Tile>> hands = new ArrayList<>();
        int next = 0;
        for (int i = 0; i < playerIds.size(); i++) {
            hands.add(new ArrayList<>(all.subList(next, next + each)));
            next += each;
        }
        List<Tile> black = new ArrayList<>();
        List<Tile> white = new ArrayList<>();
        for (Tile t : all.subList(next, all.size())) {
            (t.color() == Tile.Color.BLACK ? black : white).add(t);
        }
        return new GameState(playerIds, hands, black, white, random);
    }

    /** 나눠 줄 타일을 직접 정해서 만든다. 시험 코드용. */
    GameState(List<Integer> playerIds, List<List<Tile>> hands, List<Tile> black, List<Tile> white,
            Random random) {
        checkPlayers(playerIds);
        if (hands.size() != playerIds.size()) {
            throw new IllegalArgumentException("플레이어 수와 손패 수가 다릅니다.");
        }
        for (int i = 0; i < playerIds.size(); i++) {
            Player p = new Player(playerIds.get(i));
            p.hand.addAll(hands.get(i));
            Collections.sort(p.hand);
            players.add(p);
        }
        this.blackPile = new ArrayList<>(black);
        this.whitePile = new ArrayList<>(white);
        this.random = random;
        beginTurn(0);
    }

    private static void checkPlayers(List<Integer> ids) {
        if (ids.size() < MIN_PLAYERS || ids.size() > MAX_PLAYERS) {
            throw new IllegalArgumentException("플레이어는 2~4명이어야 합니다: " + ids.size());
        }
        Set<Integer> seen = new HashSet<>();
        for (int id : ids) {
            if (!seen.add(id)) throw new IllegalArgumentException("플레이어 ID가 겹칩니다: " + id);
        }
    }

    // ---- 상태 조회 ----

    public Phase phase() {
        return phase;
    }

    public boolean isOver() {
        return phase == Phase.OVER;
    }

    /** 지금 차례인 플레이어 ID. 게임이 끝났으면 마지막 차례였던 사람. */
    public int currentPlayerId() {
        return players.get(turn).id;
    }

    /** 차례 순서대로 놓은 플레이어 ID. */
    public List<Integer> playerIds() {
        List<Integer> ids = new ArrayList<>();
        for (Player p : players) ids.add(p.id);
        return ids;
    }

    public boolean hasPlayer(int playerId) {
        return find(playerId) != null;
    }

    public boolean isAlive(int playerId) {
        Player p = find(playerId);
        return p != null && p.alive;
    }

    /**
     * 받는 사람에게 보낼 상태. 받는 사람 본인의 타일과 공개된 타일에만 숫자를 넣는다.
     * 게임에 없는 ID(관전자)를 주면 공개된 타일만 숫자가 들어간다. 게임이 끝나면 모두 공개한다.
     */
    public StateView stateFor(int viewerId) {
        boolean over = phase == Phase.OVER;
        List<SeatView> seats = new ArrayList<>();
        for (Player p : players) {
            boolean know = over || p.id == viewerId;
            StringBuilder sb = new StringBuilder();
            for (Tile t : p.hand) {
                if (sb.length() > 0) sb.append('-');
                sb.append(t.code(know));
            }
            seats.add(new SeatView(p.id, p.alive, sb.toString()));
        }
        String drawnCode = drawn == null ? "" : drawn.code(over || currentPlayerId() == viewerId);
        return new StateView(phase, currentPlayerId(), blackPile.size(), whitePile.size(), drawnCode,
                List.copyOf(seats));
    }

    // ---- 요청 처리 ----

    /** {@code DRAW⇥색}: 그 색 더미에서 무작위로 한 장 뽑는다. */
    public List<GameEvent> draw(int playerId, Tile.Color color) throws GameException {
        checkTurn(playerId);
        if (phase != Phase.DRAW) throw badMove("지금은 타일을 뽑을 수 없어요.");
        if (color == null) throw badMove("검정이나 흰 더미를 골라 주세요.");
        List<Tile> pile = color == Tile.Color.BLACK ? blackPile : whitePile;
        if (pile.isEmpty()) throw badMove("그 더미에는 타일이 없어요.");

        drawn = pile.remove(random.nextInt(pile.size()));
        phase = Phase.GUESS;
        return List.of();
    }

    /** {@code GUESS⇥대상ID⇥자리번호⇥숫자}: 상대의 숨은 타일 숫자를 추리한다. */
    public List<GameEvent> guess(int playerId, int targetId, int index, int number) throws GameException {
        checkTurn(playerId);
        if (phase != Phase.GUESS && phase != Phase.CONTINUE) throw badMove("지금은 추리할 수 없어요.");
        Player target = find(targetId);
        if (target == null || target.id == playerId || !target.alive) {
            throw badMove("살아 있는 상대의 타일을 골라 주세요.");
        }
        if (index < 0 || index >= target.hand.size()) throw badMove("없는 자리예요.");
        Tile tile = target.hand.get(index);
        if (tile.isRevealed()) throw badMove("이미 공개된 타일이에요.");
        if (number < Tile.MIN_NUMBER || number > Tile.MAX_NUMBER) throw badMove("숫자는 0~11이에요.");

        List<GameEvent> events = new ArrayList<>();
        boolean correct = tile.number() == number;
        events.add(new GameEvent.Guessed(playerId, targetId, index, number, correct));

        if (correct) {
            tile.reveal();
            if (target.allRevealed()) eliminate(target, events);
            if (aliveCount() == 1) {
                finish(events);
            } else {
                phase = Phase.CONTINUE;
            }
        } else if (drawn != null) {
            Player me = players.get(turn);
            int at = insertDrawn(me, true);
            events.add(new GameEvent.Placed(playerId, at, true));
            nextTurn(events);
        } else {
            phase = Phase.REVEAL_SELF;
        }
        return events;
    }

    /** {@code STOP}: 맞힌 뒤 멈추고, 뽑은 타일을 숨긴 채 줄에 넣는다. */
    public List<GameEvent> stop(int playerId) throws GameException {
        checkTurn(playerId);
        if (phase != Phase.CONTINUE) throw badMove("맞힌 뒤에만 멈출 수 있어요.");

        List<GameEvent> events = new ArrayList<>();
        if (drawn != null) {
            int at = insertDrawn(players.get(turn), false);
            events.add(new GameEvent.Placed(playerId, at, false));
        }
        nextTurn(events);
        return events;
    }

    /** {@code REVEAL⇥자리번호}: 틀렸는데 뽑은 타일이 없을 때 내 숨은 타일 하나를 공개한다. */
    public List<GameEvent> reveal(int playerId, int index) throws GameException {
        checkTurn(playerId);
        if (phase != Phase.REVEAL_SELF) throw badMove("지금은 내 타일을 공개할 차례가 아니에요.");
        Player me = players.get(turn);
        if (index < 0 || index >= me.hand.size()) throw badMove("없는 자리예요.");
        Tile tile = me.hand.get(index);
        if (tile.isRevealed()) throw badMove("이미 공개된 타일이에요.");

        List<GameEvent> events = new ArrayList<>();
        tile.reveal();
        if (me.allRevealed()) eliminate(me, events);
        if (aliveCount() == 1) {
            finish(events);
        } else {
            nextTurn(events);
        }
        return events;
    }

    /**
     * 기권(방을 나가거나 접속이 끊김). 그 사람 타일을 모두 공개하고 탈락시킨다.
     * {@code FORFEIT} 메시지는 처리하는 쪽이 보내므로 {@link GameEvent.Eliminated}는 넣지 않는다.
     * 그 사람 차례였으면 다음 차례로 넘기고, 1명만 남으면 게임을 끝낸다.
     * 게임이 끝났거나 이미 탈락한 사람이면 아무것도 하지 않는다.
     */
    public List<GameEvent> forfeit(int playerId) {
        Player p = find(playerId);
        List<GameEvent> events = new ArrayList<>();
        if (phase == Phase.OVER || p == null || !p.alive) return events;

        boolean wasTurn = players.get(turn) == p;
        if (wasTurn && drawn != null) insertDrawn(p, true);
        for (Tile t : p.hand) t.reveal();
        p.alive = false;
        outOrder.add(p.id);

        if (aliveCount() == 1) {
            finish(events);
        } else if (wasTurn) {
            nextTurn(events);
        }
        return events;
    }

    // ---- 내부 ----

    private void checkTurn(int playerId) throws GameException {
        if (phase == Phase.OVER) throw badMove("게임이 끝났어요.");
        if (currentPlayerId() != playerId) {
            throw new GameException(GameException.NOT_YOUR_TURN, "내 차례가 아니에요.");
        }
    }

    private static GameException badMove(String message) {
        return new GameException(GameException.BAD_MOVE, message);
    }

    private Player find(int playerId) {
        for (Player p : players) {
            if (p.id == playerId) return p;
        }
        return null;
    }

    private int aliveCount() {
        int n = 0;
        for (Player p : players) {
            if (p.alive) n++;
        }
        return n;
    }

    /** 뽑은 타일을 정렬 규칙에 맞는 자리에 넣고 그 자리번호를 돌려준다. */
    private int insertDrawn(Player p, boolean revealed) {
        Tile t = drawn;
        drawn = null;
        if (revealed) t.reveal();
        int at = 0;
        while (at < p.hand.size() && p.hand.get(at).compareTo(t) < 0) at++;
        p.hand.add(at, t);
        return at;
    }

    private void eliminate(Player p, List<GameEvent> events) {
        p.alive = false;
        outOrder.add(p.id);
        events.add(new GameEvent.Eliminated(p.id));
    }

    private void beginTurn(int index) {
        turn = index;
        drawn = null;
        phase = blackPile.isEmpty() && whitePile.isEmpty() ? Phase.GUESS : Phase.DRAW;
    }

    private void nextTurn(List<GameEvent> events) {
        int i = turn;
        do {
            i = (i + 1) % players.size();
        } while (!players.get(i).alive);
        beginTurn(i);
        events.add(new GameEvent.Turn(players.get(i).id));
    }

    private void finish(List<GameEvent> events) {
        phase = Phase.OVER;
        for (Player p : players) {
            for (Tile t : p.hand) t.reveal();
        }
        if (drawn != null) drawn.reveal();

        List<GameEvent.Rank> ranks = new ArrayList<>();
        for (Player p : players) {
            if (p.alive) ranks.add(new GameEvent.Rank(1, p.id));
        }
        for (int i = outOrder.size() - 1; i >= 0; i--) {
            ranks.add(new GameEvent.Rank(ranks.size() + 1, outOrder.get(i)));
        }
        events.add(new GameEvent.GameOver(List.copyOf(ranks)));
    }
}
