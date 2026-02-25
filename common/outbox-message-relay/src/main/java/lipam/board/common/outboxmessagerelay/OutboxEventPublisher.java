package lipam.board.common.outboxmessagerelay;

import lipam.board.common.event.Event;
import lipam.board.common.event.EventPayload;
import lipam.board.common.event.EventType;
import lipam.board.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

// OutBox 이벤트를 만드는 EventPublisher
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final Snowflake outboxIdSnowflake = new Snowflake();
    private final Snowflake eventIdSnowflake = new Snowflake();
    private final ApplicationEventPublisher applicationEventPublisher;

    // 서비스(예: article 서비스)에서 호출하는 메서드
    // EventType, EventPayload, shardKey 를 받아 Outbox 레코드를 생성하고 이벤트로 발행
    public void publish(EventType type, EventPayload payload, Long shardKey) {
        Outbox outbox = Outbox.create(
                outboxIdSnowflake.nextId(),
                type,
                Event.of(
                        eventIdSnowflake.nextId(),
                        type,
                        payload
                ).toJson(),
                // 샤딩 전략:
                // shardKey 를 SHARD_COUNT 로 나눈 나머지를 사용해
                // 특정 애플리케이션 인스턴스가 담당하도록 분배
                // 예: articleId = 10 → 10 % 4 = 2번 샤드
                shardKey % MessageRelayConstants.SHARD_COUNT
        );

        // Outbox 엔티티를 만든 뒤, applicationEventPublisher 를 통해 이벤트를 발행한다.
        // 이후 MessageRelay 쪽에서 이 이벤트를 받아 Kafka 전송을 처리한다. (트랜잭션 로직과 Kafka 전송 로직을 분리하기 위한 구조이다.)
        applicationEventPublisher.publishEvent(OutboxEvent.of(outbox));
    }

}
