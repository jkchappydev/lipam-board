package lipam.board.articleread.service.event.handler;

import lipam.board.articleread.repository.ArticleQueryModelRepository;
import lipam.board.common.event.Event;
import lipam.board.common.event.EventType;
import lipam.board.common.event.payload.CommentDeletedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 댓글 삭제 이벤트를 기반으로 Read Model 의 감소된 댓글 수 집계값을 동기화하는 핸들러
@Component
@RequiredArgsConstructor
public class CommentDeletedEventHandler implements EventHandler<CommentDeletedEventPayload> {

    private final ArticleQueryModelRepository articleQueryModelRepository;

    @Override
    public void handle(Event<CommentDeletedEventPayload> event) {
        // COMMENT_DELETED 이벤트 발생 시,
        // Redis(Read Model)에 저장된 ArticleQueryModel 을 조회한 뒤
        // 감소된 댓글 수 정보를 반영하여
        // 조회 모델(Read Model)을 최신 상태로 동기화한다.
        articleQueryModelRepository.read(event.getPayload().getArticleId())
                .ifPresent(articleQueryModel -> {
                    articleQueryModel.updateBy(event.getPayload());
                    articleQueryModelRepository.update(articleQueryModel);
                });
    }

    @Override
    public boolean supports(Event<CommentDeletedEventPayload> event) {
        return EventType.COMMENT_DELETED == event.getType();
    }

}
