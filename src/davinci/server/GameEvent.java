package davinci.server;

import java.util.List;

/**
 * 게임 요청을 처리한 결과로 방 안 모두에게 알릴 일. 각 종류가 프로토콜 메시지 하나와 맞는다.
 * 숨은 타일의 숫자는 들어 있지 않으므로 모두에게 그대로 보내도 된다.
 */
public sealed interface GameEvent {

    /** {@code GUESSED⇥추리자ID⇥대상ID⇥자리번호⇥숫자⇥결과}. 숫자는 추리한 사람이 말한 값이다. */
    record Guessed(int guesserId, int targetId, int index, int number, boolean correct) implements GameEvent {
    }

    /** {@code PLACED⇥플레이어ID⇥자리번호⇥공개여부}. 넣은 타일의 숫자는 담지 않는다. */
    record Placed(int playerId, int index, boolean revealed) implements GameEvent {
    }

    /** {@code ELIM⇥플레이어ID}. */
    record Eliminated(int playerId) implements GameEvent {
    }

    /** {@code TURN⇥플레이어ID}. */
    record Turn(int playerId) implements GameEvent {
    }

    /** {@code GAMEOVER⇥순위목록}. 순위 1이 승자다. 이 메시지는 마지막 {@code STATE} 뒤에 보낸다. */
    record GameOver(List<Rank> ranks) implements GameEvent {
    }

    /** 순위 한 줄. */
    record Rank(int rank, int playerId) {
    }
}
