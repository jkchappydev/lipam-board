package lipam.board.hotarticle.service.eventhandler;

import lipam.board.common.event.Event;
import lipam.board.common.event.EventType;
import lipam.board.common.event.payload.ArticleDeletedEventPayload;
import lipam.board.hotarticle.repository.ArticleCreatedTimeRepository;
import lipam.board.hotarticle.repository.HotArticleListRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArticleDeletedEventHandler implements EventHandler<ArticleDeletedEventPayload> {

    private final HotArticleListRepository hotArticleListRepository; // 삭제 이벤트는 HotArticleListRepository 에서도 삭제 되어야 한다.
    private final ArticleCreatedTimeRepository articleCreatedTimeRepository; // ArticleCreatedTimeRepository 에서도 삭제 되어야 한다.

    @Override
    public void handle(Event<ArticleDeletedEventPayload> event) {
        ArticleDeletedEventPayload payload = event.getPayload();
        articleCreatedTimeRepository.delete(payload.getArticleId());
        hotArticleListRepository.remove(payload.getArticleId(), payload.getCreatedAt());
    }

    @Override
    public boolean supports(Event<ArticleDeletedEventPayload> event) {
        return EventType.ARTICLE_DELETED == event.getType();
    }

    @Override
    public Long findArticleId(Event<ArticleDeletedEventPayload> event) {
        return event.getPayload().getArticleId();
    }

}
