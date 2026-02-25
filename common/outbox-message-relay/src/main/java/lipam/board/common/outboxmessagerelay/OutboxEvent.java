package lipam.board.common.outboxmessagerelay;

import lombok.Getter;
import lombok.ToString;

// Outbox 객체를 이벤트 형태로 전달하기 위한 래퍼 클래스
@Getter
@ToString
public class OutboxEvent {

    private Outbox outbox;

    // Outbox 를 그대로 넘기지 않고, applicationEventPublisher 로 전달하기 위한 이벤트 객체로 감싸서 생성
    // Outbox 엔티티는 DB 저장용 객체이고, OutboxEvent 는 이를 전달하기 위한 이벤트 객체이다.
    // 역할을 분리하기 위해 Outbox 를 직접 발행하지 않고 이벤트 전달용 객체로 감싸서 사용한다.
    public static OutboxEvent of(Outbox outbox) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.outbox = outbox;

        return outboxEvent;
    }

}