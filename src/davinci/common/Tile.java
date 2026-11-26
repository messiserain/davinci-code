package davinci.common;

/**
 * 타일 하나. 색과 숫자는 처음부터 정해져 있고, 공개 여부만 바뀐다.
 * 같은 색·숫자의 타일은 한 개뿐이므로 타일끼리는 객체 자체로 구분한다.
 */
public final class Tile implements Comparable<Tile> {

    public static final int MIN_NUMBER = 0;
    public static final int MAX_NUMBER = 11;

    /** 타일 색. 숫자가 같으면 검정이 왼쪽에 온다(선언 순서). */
    public enum Color {
        BLACK('B'), WHITE('W');

        private final char code;

        Color(char code) {
            this.code = code;
        }

        public char code() {
            return code;
        }

        /** "B" 또는 "W"를 색으로 바꾼다. 다른 값이면 null. */
        public static Color fromCode(String s) {
            if ("B".equals(s)) return BLACK;
            if ("W".equals(s)) return WHITE;
            return null;
        }
    }

    private final Color color;
    private final int number;
    private boolean revealed;

    public Tile(Color color, int number) {
        if (color == null) throw new IllegalArgumentException("색이 없습니다.");
        if (number < MIN_NUMBER || number > MAX_NUMBER) {
            throw new IllegalArgumentException("타일 숫자는 0~11입니다: " + number);
        }
        this.color = color;
        this.number = number;
    }

    public Color color() {
        return color;
    }

    public int number() {
        return number;
    }

    public boolean isRevealed() {
        return revealed;
    }

    public void reveal() {
        revealed = true;
    }

    /** 정렬 규칙: 숫자가 작은 것이 왼쪽, 숫자가 같으면 검정이 왼쪽. */
    @Override
    public int compareTo(Tile o) {
        if (number != o.number) return Integer.compare(number, o.number);
        return color.compareTo(o.color);
    }

    /**
     * 프로토콜의 타일 표기. 예: {@code B5}, {@code W11*}, {@code B?}.
     *
     * @param knowNumber 받는 사람이 이 타일의 숫자를 알아도 되면 true.
     *                   공개된 타일은 이 값과 상관없이 숫자를 넣는다.
     */
    public String code(boolean knowNumber) {
        StringBuilder sb = new StringBuilder(4).append(color.code);
        if (revealed) {
            sb.append(number).append('*');
        } else if (knowNumber) {
            sb.append(number);
        } else {
            sb.append('?');
        }
        return sb.toString();
    }

    /** 디버그용. 숫자를 모두 드러내므로 메시지에 쓰지 않는다. */
    @Override
    public String toString() {
        return code(true);
    }
}
