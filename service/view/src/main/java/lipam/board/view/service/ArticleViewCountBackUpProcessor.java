package lipam.board.view.service;

import lipam.board.common.event.EventType;
import lipam.board.common.event.payload.ArticleViewedEventPayload;
import lipam.board.common.outboxmessagerelay.OutboxEventPublisher;
import lipam.board.view.entity.ArticleViewCount;
import lipam.board.view.repository.ArticleViewCountBackUpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ArticleViewCountBackUpProcessor {

    private final ArticleViewCountBackUpRepository articleViewCountBackUpRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void backUp(Long articleId, Long viewCount) {
        int result = articleViewCountBackUpRepository.updateViewCount(articleId, viewCount);
        if (result == 0) {
            articleViewCountBackUpRepository.findById(articleId)
                    .ifPresentOrElse(ignored -> {},
                            () -> articleViewCountBackUpRepository.save(
                                ArticleViewCount.init(articleId, viewCount)
                        )
                    );
        }

        // 조회수 백업 이벤트 발행
        // 조회수 백업 시 Outbox에 이벤트 저장 → 트랜잭션 커밋 후 Kafka 전송 → articleId 기준 샤드 라우팅으로 게시글 단위 순서 보장
        // - type: ARTICLE_VIEWED
        // - payload: 게시글 ID + 현재 누적 조회수(articleViewCount)
        // - shardKey: articleId 기준 샤드 라우팅 (같은 게시글에 대한 이벤트는 동일 샤드에서 순서 보장)
        outboxEventPublisher.publish(
                EventType.ARTICLE_VIEWED,
                ArticleViewedEventPayload.builder()
                        .articleId(articleId)
                        .articleViewCount(viewCount)
                        .build(),
                articleId
        );
    }

}
