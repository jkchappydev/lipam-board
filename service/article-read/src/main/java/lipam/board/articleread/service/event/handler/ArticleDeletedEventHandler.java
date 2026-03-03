package lipam.board.articleread.service.event.handler;

import lipam.board.articleread.repository.ArticleIdListRepository;
import lipam.board.articleread.repository.ArticleQueryModelRepository;
import lipam.board.articleread.repository.BoardArticleCountRepository;
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
    private final ArticleIdListRepository articleIdListRepository;
    private final BoardArticleCountRepository boardArticleCountRepository;

    @Override
    public void handle(Event<ArticleDeletedEventPayload> event) {
        // ARTICLE_DELETED 이벤트 발생 시,
        // Redis(Read Model)에 저장된 ArticleQueryModel 을 삭제하여 조회 모델을 동기화한다.
        ArticleDeletedEventPayload payload = event.getPayload();
        // 순서 중요
        articleIdListRepository.delete(payload.getBoardId(), payload.getArticleId());
        articleQueryModelRepository.delete(payload.getArticleId());
        boardArticleCountRepository.createOrUpdate(payload.getBoardId(), payload.getBoardArticleCount());

        // 이 순서라면, 목록에는 있지만 게시글 조회는 안되는 상황이 일어날 수 있음
        /*
        articleQueryModelRepository.delete(payload.getArticleId());
        articleIdListRepository.delete(payload.getBoardId(), payload.getArticleId());
        boardArticleCountRepository.createOrUpdate(payload.getBoardId(), payload.getBoardArticleCount());
        */
    }

    @Override
    public boolean supports(Event<ArticleDeletedEventPayload> event) {
        return EventType.ARTICLE_DELETED == event.getType();
    }

}
