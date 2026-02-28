package lipam.board.articleread.service.event.handler;

import lipam.board.articleread.repository.ArticleQueryModelRepository;
import lipam.board.common.event.Event;
import lipam.board.common.event.EventType;
import lipam.board.common.event.payload.ArticleLikedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 게시글 좋아요 이벤트를 기반으로 Read Model 의 증가된 좋아요 수 집계값을 동기화하는 핸들러
@Component
@RequiredArgsConstructor
public class ArticleLikedEventHandler implements EventHandler<ArticleLikedEventPayload> {

    private final ArticleQueryModelRepository articleQueryModelRepository;

    @Override
    public void handle(Event<ArticleLikedEventPayload> event) {
        // ARTICLE_LIKED 이벤트 발생 시,
        // Redis(Read Model)에 저장된 ArticleQueryModel 에
        // 좋아요 증가 처리 결과를 반영하여 조회 모델(Read Model)을 최신 상태로 동기화한다.
        articleQueryModelRepository.read(event.getPayload().getArticleId())
                .ifPresent(articleQueryModel -> {
                    articleQueryModel.updateBy(event.getPayload());
                    articleQueryModelRepository.update(articleQueryModel);
                });
    }

    @Override
    public boolean supports(Event<ArticleLikedEventPayload> event) {
        return EventType.ARTICLE_LIKED == event.getType();
    }

}
