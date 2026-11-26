package davinci.common;

/** 게임 단계. 이름이 그대로 {@code STATE} 메시지의 단계 값이 된다. */
public enum Phase {
    /** 차례인 사람이 더미에서 뽑을 차례. */
    DRAW,
    /** 뽑은 조커를 놓을 자리를 고를 차례 [중]. 아직 쓰지 않는다. */
    JOKER,
    /** 상대 타일 하나를 골라 추리할 차례. */
    GUESS,
    /** 맞힌 뒤 계속 추리할지 멈출지 고를 차례. */
    CONTINUE,
    /** 틀렸는데 뽑은 타일이 없어서 내 숨은 타일 하나를 공개할 차례. */
    REVEAL_SELF,
    /** 게임 끝. */
    OVER
}
