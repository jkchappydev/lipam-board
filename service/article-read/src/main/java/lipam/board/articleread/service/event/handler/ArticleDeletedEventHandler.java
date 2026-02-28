package lipam.board.articleread.service.event.handler;

import lipam.board.articleread.repository.ArticleQueryModelRepository;
import lipam.board.common.event.Event;
import lipam.board.common.event.EventType;
import lipam.board.common.event.payload.ArticleDeletedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 게시글 삭제 이벤트를 기반으로 Read Model에서 해당 게시글 데이터를 제거하는 핸들러
@Component
@RequiredArgsConstructor
public class ArticleDeletedEventHandler implements EventHandler<ArticleDeletedEventPayload> {

    private final ArticleQueryModelRepository articleQueryModelRepository;

    @Override
    public void handle(Event<ArticleDeletedEventPayload> event) {
        // ARTICLE_DELETED 이벤트 발생 시,
        // Redis(Read Model)에 저장된 ArticleQueryModel을 삭제하여 조회 모델을 동기화한다.
        ArticleDeletedEventPayload payload = event.getPayload();
        articleQueryModelRepository.delete(payload.getArticleId());
    }

    @Override
    public boolean supports(Event<ArticleDeletedEventPayload> event) {
        return EventType.ARTICLE_DELETED == event.getType();
    }

}
