package lipam.board.articleread.service.event.handler;

import lipam.board.articleread.repository.ArticleQueryModelRepository;
import lipam.board.common.event.Event;
import lipam.board.common.event.EventType;
import lipam.board.common.event.payload.ArticleUpdatedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 게시글 수정 이벤트를 기반으로 Read Model 내용을 갱신하는 핸들러
@Component
@RequiredArgsConstructor
public class ArticleUpdatedEventHandler implements EventHandler<ArticleUpdatedEventPayload> {

    private final ArticleQueryModelRepository articleQueryModelRepository;

    @Override
    public void handle(Event<ArticleUpdatedEventPayload> event) {
        // ARTICLE_UPDATED 이벤트 발생 시,
        // Redis(Read Model)에 저장된 ArticleQueryModel 을 조회한 뒤
        // 수정된 게시글 정보(title, content 등)를 반영하여
        // 조회 모델(Read Model)을 최신 상태로 동기화한다.
        articleQueryModelRepository.read(event.getPayload().getArticleId())
                .ifPresent(articleQueryModel -> {
                    articleQueryModel.updateBy(event.getPayload());
                    articleQueryModelRepository.update(articleQueryModel);
                });
    }

    @Override
    public boolean supports(Event<ArticleUpdatedEventPayload> event) {
        return EventType.ARTICLE_UPDATED == event.getType();
    }

}
