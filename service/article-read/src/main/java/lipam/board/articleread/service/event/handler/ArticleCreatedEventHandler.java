package lipam.board.articleread.service.event.handler;

import lipam.board.articleread.repository.ArticleQueryModel;
import lipam.board.articleread.repository.ArticleQueryModelRepository;
import lipam.board.common.event.Event;
import lipam.board.common.event.EventType;
import lipam.board.common.event.payload.ArticleCreatedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 게시글 생성 이벤트를 기반으로 Read Model에 조회용 게시글 데이터를 생성하는 핸들러
@Component
@RequiredArgsConstructor
public class ArticleCreatedEventHandler implements EventHandler<ArticleCreatedEventPayload> {

    private final ArticleQueryModelRepository articleQueryModelRepository;

    @Override
    public void handle(Event<ArticleCreatedEventPayload> event) {
        // ARTICLE_CREATED 이벤트 발생 시,
        // Redis(Read Model)에 ArticleQueryModel 을 생성하여 조회 모델을 동기화한다.
        ArticleCreatedEventPayload payload = event.getPayload();
        articleQueryModelRepository.create(
                ArticleQueryModel.create(payload),
                Duration.ofDays(1)
        );
    }

    @Override
    public boolean supports(Event<ArticleCreatedEventPayload> event) {
        return EventType.ARTICLE_CREATED == event.getType();
    }

}
