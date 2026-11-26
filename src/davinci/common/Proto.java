package davinci.common;

import java.util.Collection;

/**
 * 클라이언트와 서버가 함께 쓰는 통신 규약 (3주차: 귓속말 추가).
 *
 * 메시지 하나 = 명령어 + 탭 + 값 + 탭 + 값 …  을 writeUTF 한 번으로 보낸다 (수업 코드와 같은 방식).
 * 값을 공백이 아니라 탭으로 나누는 이유: 닉네임·채팅·방 제목에 공백이 들어가기 때문이다.
 * 여러 개를 담는 값(접속자 목록)은 항목을 RS(\u001E), 항목 안의 값을 US(\u001F)로 나눈다.
 * 사용자가 입력한 글자는 {@link #clean}으로 제어 문자를 지워서 구분자와 절대 섞이지 않게 한다.
 */
public final class Proto {
    private Proto() { }

    public static final int PORT = 30000;

    public static final char FS = '\t';     // 값 구분
    public static final char RS = '\u001E'; // 목록의 항목 구분
    public static final char US = '\u001F'; // 항목 안의 값 구분

    // ---- 클라이언트 → 서버 (2주차 구현) ----
    public static final String HELLO = "HELLO";     // 닉네임
    public static final String CHAT = "CHAT";       // 내용

    // ---- 클라이언트 → 서버 (3주차 추가) ----
    public static final String WHISPER = "WHISPER"; // 받는닉네임, 내용
    // 서버 → 보낸 사람과 받는 사람 둘에게만 WHISPER: 보낸ID, 보낸닉네임, 받는닉네임, 내용

    // ---- 서버 → 클라이언트 (2주차 구현) ----
    public static final String WELCOME = "WELCOME"; // 내ID, 닉네임
    public static final String ERROR = "ERROR";     // 코드, 안내문구
    public static final String USERS = "USERS";     // 접속자 목록 (ID US 닉네임) RS …
    public static final String SYS = "SYS";         // 안내문구 (입장·퇴장)
    // 서버 → 클라이언트 CHAT: 보낸ID, 닉네임, 내용

    /** 명령어와 값들을 탭으로 이어 한 줄로 만든다. */
    public static String encode(String cmd, Object... fields) {
        StringBuilder sb = new StringBuilder(cmd);
        for (Object f : fields) sb.append(FS).append(f == null ? "" : f.toString());
        return sb.toString();
    }

    /** 목록의 항목 하나 (값들을 US로 잇는다). */
    public static String record(Object... values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(US);
            sb.append(values[i]);
        }
        return sb.toString();
    }

    /** 항목들을 RS로 잇는다. */
    public static String list(Collection<String> records) {
        return String.join(String.valueOf(RS), records);
    }

    /** 사용자 입력에서 제어 문자(탭·줄바꿈·구분자 포함)를 공백으로 바꾸고, 앞뒤 공백을 지우고, 길이를 자른다. */
    public static String clean(String s, int maxLen) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c < 0x20 || c == 0x7F ? ' ' : c);
        }
        String r = sb.toString().trim();
        return r.length() > maxLen ? r.substring(0, maxLen).trim() : r;
    }

    /** 로그에서 보기 좋게 탭을 ⇥로, 목록 구분자를 │ , 로 바꾼다. */
    public static String pretty(String line) {
        return line.replace(FS, '⇥').replace(RS, '│').replace(US, ',');
    }
}
