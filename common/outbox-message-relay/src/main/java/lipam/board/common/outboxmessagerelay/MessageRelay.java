package lipam.board.common.outboxmessagerelay;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRelay {

    private final OutboxRepository outboxRepository;
    private final MessageRelayCoordinator messageRelayCoordinator; // 현재 실행 중인 애플리케이션 목록/샤드 할당 계산용
    private final KafkaTemplate<String, String> messageRelayKafkaTemplate; // Kafka 로 이벤트를 전송하기 위한 템플릿

    // 트랜잭션 커밋 직전에 OutboxEvent 를 받아 outbox 테이블에 저장한다.
    // 비즈니스 데이터 변경과 outbox 저장이 같은 단일 트랜잭션으로 묶이도록 한다.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void createOutbox(OutboxEvent outboxEvent) {
        log.info("[MessageRelay.createOutbox] outBoxEvent={}", outboxEvent);
        outboxRepository.save(outboxEvent.getOutbox());
    }

    // 트랜잭션 커밋 이후 Kafka 전송은,
    // MessageRelayConfig 에서 정의한 전용 비동기 스레드 풀(messageRelayPublishEventExecutor)에서 처리하도록 한다.
    @Async("messageRelayPublishEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT) // 트랜잭션이 정상적으로 커밋된 이후에만 실행된다. (DB 반영이 완료된 상태에서 Kafka 전송을 시도하기 위함)
    public void publishEvent(OutboxEvent outboxEvent) {
        // 실제 이벤트 발행: OutboxEvent 에서 Outbox 만 꺼내 실제 Kafka 전송 로직을 수행한다.
        publishEvent(outboxEvent.getOutbox());
    }

    // Kafka 로 전송하고 성공하면 outbox 테이블에서 해당 레코드를 삭제한다.
    // 실패하면 outbox 에 남겨두고, 스케줄러가 나중에 재전송한다.
    private void publishEvent(Outbox outbox) {
        try {
            messageRelayKafkaTemplate.send( // KafkaTemplate 으로 전송
                    outbox.getEventType().getTopic(),       // 전송할 Kafka Topic
                    String.valueOf(outbox.getShardKey()),   // Kafka 에 전송하는 Key, 근데 이게 ShardKey 이면 동일한 Kafka 파티션으로 전송된다. 근데 동일한 Kafka 파티션으로 전송이 되면 순서가 보장된다. 그래서 Outbox 가 동일한 ShardKey 에 대해서는 Kafka 에서 동일한 순서대로 전송된다는 걸 알 수 있다.
                    outbox.getPayload()                     // 실제 전송할 메시지(payload JSON)
            ).get(1, TimeUnit.SECONDS); // send() 비동기라서 CompletableFuture 를 반환하기때문에 get()으로 결과를 기다리고, 1초 타임아웃 설정

            // Kafka 전송이 성공했으면 outbox 는 처리 완료이므로 삭제
            outboxRepository.delete(outbox);
        } catch (Exception e) {
            // 여기서 실패하면 outbox는 삭제되지 않고 남는다 → 스케줄러가 재전송 대상이 됨
            log.error("[MessageRelay.publishEvent] outbox={}", outbox, e);
        }
    }

    // 일정 주기(10초)마다 outbox 에 남아있는 "미전송 이벤트"를 조회해서 Kafka 로 재전송한다.
    // (AFTER_COMMIT 비동기 전송이 실패했거나, 일시 장애로 못 보낸 이벤트를 복구하는 용도)
    @Scheduled(
            fixedDelay = 10, // 10초마다 실행
            initialDelay = 5, // 애플리케이션 시작 후 5초 뒤 최초 실행
            timeUnit = TimeUnit.SECONDS, // 시간 단위: 초
            scheduler = "messageRelayPublishPendingEventExecutor" // MessageRelayConfig 에서 정의한 전용 스케줄러 사용
    )
    public void publishPendingEvent() {
        AssignedShard assignedShard = messageRelayCoordinator.assignedShard(); // 현재 실행 중인 애플리케이션이 담당하는 샤드 목록 조회
        log.info("[MessageRelay.publishPendingEvent] assignedShard size={}", assignedShard.getShards().size()); // 애플리케이션에 몇개의 샤드가 할당되었는지 확인

        // 현재 애플리케이션이 담당하는 샤드에 대해서만 polling 수행해서 Kafka 에 전송한다. (샤드 분산 처리)
        for (Long shard : assignedShard.getShards()) {
            // 생성된지 10초 이상 지난(outbox 에 계속 남아있는) 이벤트만 조회
            // (10초 설정한 이유: 너무 최근 건은 AFTER_COMMIT 비동기 전송이 처리 중일 수 있으니 제외)
            List<Outbox> outboxes = outboxRepository.findAllByShardKeyAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
                    shard,
                    LocalDateTime.now().minusSeconds(10), // 생성된지 10초 지난 이벤트
                    Pageable.ofSize(100) // 한번 조회시 100개 만 조회
            );

            // 조회된 outbox 들을 순차적으로 Kafka 로 전송 시도
            for (Outbox outbox : outboxes) {
                publishEvent(outbox); // 전송
            }
        }
    }
}
