package davinci.server;

/**
 * 게임 진행 명령어(방 만들기·준비·시작·타일·추리)를 서버에 등록하는 곳. 게임 담당이 채운다.
 *
 * 채팅 담당 코드(Hub)를 고치지 않고 여기서만 명령어를 더하면 된다. 예)
 * <pre>
 *   hub.on("READY", (u, m) -> {
 *       // u: 보낸 사람, m.s(0)/m.i(0, 기본값): 받은 값
 *       hub.broadcast(Proto.encode("SYS", u.name + "님이 준비했어요."));   // 모두에게 (WriteAll)
 *       u.send(Proto.encode("ROOM", ...));                                 // 한 명에게 (WriteOne)
 *   });
 *   hub.onLeave(u -> { ... });   // 게임 중에 나간 사람 정리
 * </pre>
 * 모든 처리는 게임 로직 스레드 하나에서 순서대로 실행되므로 synchronized 없이 상태를 바꿔도 된다.
 */
final class GameHandlers {
    private GameHandlers() { }

    static void register(Hub hub) {
        // 아직 없음 (게임 담당 구현 예정)
    }
}
