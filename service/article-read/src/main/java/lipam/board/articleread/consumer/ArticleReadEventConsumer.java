package lipam.board.articleread.consumer;

import lipam.board.articleread.service.ArticleReadService;
import lipam.board.common.event.Event;
import lipam.board.common.event.EventPayload;
import lipam.board.common.event.EventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleReadEventConsumer {

    private final ArticleReadService articleReadService;

    @KafkaListener(topics = {
            EventType.Topic.LIPAM_BOARD_ARTICLE,
            EventType.Topic.LIPAM_BOARD_COMMENT,
            EventType.Topic.LIPAM_BOARD_LIKE
    }) // 구독한 3개 Topic 으로 부터 메시지 오면 listen() 호출
    public void listen(String message, Acknowledgment ack) {
        // message:
        // Kafka 에서 받은 원본 메시지(대부분 JSON 문자열)
        // -> Event.fromJson(message)로 Event 객체로 파싱해서 사용함

        // ack(Acknowledgment):
        // 메시지 처리가 정상적으로 끝났다고 Kafka 에 직접 알려줄 때 사용하는 객체
        // -> ack.acknowledge() 호출하면 오프셋 커밋돼서 재처리 안 함

        // Kafka 에서 받은 원본 메시지(JSON 문자열) 로그 출력
        log.info("[ArticleReadEventConsumer.listen] message={}", message);
        // message 를 Event 로 만듦

        // 이벤트 객체가 정상적으로 만들어졌으면(null 아니면)
        Event<EventPayload> event = Event.fromJson(message);
        if (event != null) {
            // 이벤트를 처리할 수 있는 EventHandler 를 찾아서
            // 해당 이벤트 타입(게시글/댓글/좋아요 등)에 맞는 로직을 실행함
            articleReadService.handleEvent(event);
        }

        // 레코드 처리가 끝났다고 카프카에 알림
        ack.acknowledge();
    }

}
