package davinci.server;

/**
 * 규칙에 맞지 않는 게임 요청. 처리하는 쪽은 {@code ERROR⇥코드⇥안내문구}로 요청한 사람에게만 돌려준다.
 * 이 예외가 나면 게임 상태는 바뀌지 않는다.
 */
public class GameException extends Exception {

    private static final long serialVersionUID = 1L;

    public static final String NOT_YOUR_TURN = "NOT_YOUR_TURN";
    public static final String BAD_MOVE = "BAD_MOVE";

    private final String code;

    public GameException(String code, String message) {
        super(message);
        this.code = code;
    }

    /** 프로토콜 오류 코드({@code NOT_YOUR_TURN}, {@code BAD_MOVE}). */
    public String code() {
        return code;
    }
}
