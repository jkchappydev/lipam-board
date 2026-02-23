package lipam.board.hotarticle.consumer;

import lipam.board.common.event.Event;
import lipam.board.common.event.EventPayload;
import lipam.board.common.event.EventType;
import lipam.board.hotarticle.service.HotArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HotArticleEventConsumer {

    private final HotArticleService hotArticleService;

    // @KafkaListener: 지정한 토픽을 구독하고, 메시지가 들어오면 이 메서드가 호출되어 이벤트 처리 흐름을 시작한다.
    @KafkaListener(topics = {
            EventType.Topic.LIPAM_BOARD_ARTICLE,
            EventType.Topic.LIPAM_BOARD_COMMENT,
            EventType.Topic.LIPAM_BOARD_LIKE,
            EventType.Topic.LIPAM_BOARD_VIEW
    })
    public void listen(String message, Acknowledgment ack) {
        log.info("[HotArticleEventConsumer.listen] received message={}", message);
        Event<EventPayload> event = Event.fromJson(message); // 응답받은 Kafka message(JSON 문자열)를 서비스 로직에서 사용하려고 Event 객체로 변환(역직렬화)한다.
        if (event != null) { // 역직렬화에 성공한 경우에만,
            hotArticleService.handleEvent(event); // 이벤트 타입에 따라 인기글 로직(집계/점수/목록 반영)을 수행한다.
        }
        ack.acknowledge(); // 현재 레코드 처리가 끝났음을 Kafka 에 커밋(ACK)하여 재처리되지 않게 한다.
    }

}
