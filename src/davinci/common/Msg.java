package davinci.common;

import java.util.ArrayList;
import java.util.List;

/** 받은 한 줄을 명령어와 값들로 나눈 것. 값이 없거나 숫자가 아니어도 예외 없이 기본값을 돌려준다. */
public final class Msg {
    public final String cmd;
    private final String[] fields;

    private Msg(String cmd, String[] fields) {
        this.cmd = cmd;
        this.fields = fields;
    }

    public static Msg parse(String line) {
        // -1: 끝에 빈 값이 와도(예: 비밀번호를 비운 경우) 값의 개수가 줄지 않게 한다
        String[] parts = line.split(String.valueOf(Proto.FS), -1);
        String[] f = new String[parts.length - 1];
        System.arraycopy(parts, 1, f, 0, f.length);
        return new Msg(parts[0].trim().toUpperCase(), f);
    }

    /** 값의 개수 */
    public int size() {
        return fields.length;
    }

    public String s(int i) {
        return i < fields.length ? fields[i] : "";
    }

    public int i(int i, int def) {
        try {
            return Integer.parseInt(s(i).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** i번째 값을 목록으로 읽는다: 항목마다 값 배열 */
    public List<String[]> list(int i) {
        List<String[]> out = new ArrayList<>();
        String v = s(i);
        if (v.isEmpty()) return out;
        for (String rec : v.split(String.valueOf(Proto.RS), -1)) out.add(rec.split(String.valueOf(Proto.US), -1));
        return out;
    }
}
