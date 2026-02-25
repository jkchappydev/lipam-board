package lipam.board.common.outboxmessagerelay;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE) // 인스턴스 생성을 막기 위한 private 생성자
public final class MessageRelayConstants { // 상수 클래스(또는 유틸성 클래스)는 상속을 방지하기 위해 final 을 붙인다.
    // 애플리케이션마다 샤드를 적절하게 분산시켜 이벤트 전송을 나눠서 처리하도록 하기 위한 상수 값 (임의로 4개의 샤드가 있다고 가정한다.)
    public static final int SHARD_COUNT = 4;

}