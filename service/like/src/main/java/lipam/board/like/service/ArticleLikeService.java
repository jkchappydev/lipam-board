package lipam.board.like.service;

import lipam.board.common.event.EventType;
import lipam.board.common.event.payload.ArticleLikedEventPayload;
import lipam.board.common.event.payload.ArticleUnlikedEventPayload;
import lipam.board.common.outboxmessagerelay.OutboxEventPublisher;
import lipam.board.common.snowflake.Snowflake;
import lipam.board.like.entity.ArticleLike;
import lipam.board.like.entity.ArticleLikeCount;
import lipam.board.like.repository.ArticleLikeCountRepository;
import lipam.board.like.repository.ArticleLikeRepository;
import lipam.board.like.service.response.ArticleLikeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ArticleLikeService {

    private final Snowflake snowflake = new Snowflake();
    private final ArticleLikeRepository articleLikeRepository;
    private final ArticleLikeCountRepository articleLikeCountRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    public ArticleLikeResponse read(
            Long articleId,
            Long userId
    ) {
        return articleLikeRepository.findByArticleIdAndUserId(articleId, userId)
                .map(ArticleLikeResponse::from)
                .orElseThrow();
    }

    /**
     * 비관적 락 - 방법 1
     * update 구문
     */
    @Transactional
    public void likePessimisticLock1(Long articleId, Long userId) {
        ArticleLike articleLike = articleLikeRepository.save(
                ArticleLike.create(
                        snowflake.nextId(),
                        articleId,
                        userId
                )
        );

        int result = articleLikeCountRepository.increase(articleId);
        if (result == 0) {
            // 최초 요청 시에는 update 되는 레코드가 없으므로, 1로 초기화 한다.
            // 트래픽이 순식간에 몰릴 수 있는 상황에는 유실될 수 있으므로, 게시글 생성 시점에 미리 0으로 초기화 해둘수도 있다.
            articleLikeCountRepository.save(
                    ArticleLikeCount.init(articleId, 1L)
            );
        }

        // 좋아요 생성 이벤트 발행
        // 좋아요 생성 시 Outbox에 이벤트 저장 → 트랜잭션 커밋 후 Kafka 전송 → articleId 기준 샤드 라우팅으로 게시글 단위 순서 보장
        // - type: ARTICLE_LIKED
        // - payload: 좋아요 정보 + 현재 게시글의 전체 좋아요 수(articleLikeCount)
        // - shardKey: articleId 기준으로 샤드 라우팅 (같은 게시글 이벤트는 동일 샤드에서 순서 보장)
        outboxEventPublisher.publish(
                EventType.ARTICLE_LIKED,
                ArticleLikedEventPayload.builder()
                        .articleLikeId(articleLike.getArticleLikeId())
                        .articleId(articleLike.getArticleId())
                        .userId(articleLike.getUserId())
                        .createdAt(articleLike.getCreatedAt())
                        .articleLikeCount(count(articleLike.getArticleId()))
                        .build(),
                articleLike.getArticleId()
        );
    }

    @Transactional
    public void unlikePessimisticLock1(Long articleId, Long userId) {
        articleLikeRepository.findByArticleIdAndUserId(articleId, userId)
                .ifPresent(articleLike -> {
                    articleLikeRepository.delete(articleLike);
                    articleLikeCountRepository.decrease(articleId);

                    // 좋아요 취소 이벤트 발행
                    // 좋아요 취소 시 Outbox에 이벤트 저장 → 트랜잭션 커밋 후 Kafka 전송 → articleId 기준 샤드 라우팅으로 게시글 단위 순서 보장
                    // - type: ARTICLE_UNLIKED
                    // - payload: 좋아요 취소 정보 + 현재 게시글의 전체 좋아요 수
                    // - shardKey: articleId 기준 샤드 라우팅 (같은 게시글에 대한 이벤트는 동일 샤드에서 처리)
                    outboxEventPublisher.publish(
                            EventType.ARTICLE_UNLIKED,
                            ArticleUnlikedEventPayload.builder()
                                    .articleLikeId(articleLike.getArticleLikeId())
                                    .articleId(articleLike.getArticleId())
                                    .userId(articleLike.getUserId())
                                    .createdAt(articleLike.getCreatedAt())
                                    .articleLikeCount(count(articleLike.getArticleId()))
                                    .build(),
                            articleLike.getArticleId()
                    );
                });
    }

    /**
     * 비관적 락 - 방법 2
     * select ... for update + update 구문
     */
    @Transactional
    public void likePessimisticLock2(Long articleId, Long userId) {
        articleLikeRepository.save(
                ArticleLike.create(
                        snowflake.nextId(),
                        articleId,
                        userId
                )
        );

        // 조회된 데이터에 대해서 비관적 락이 잡힌다.
        ArticleLikeCount articleLikeCount = articleLikeCountRepository.findLockedByArticleId(articleId)
                .orElseGet(() -> ArticleLikeCount.init(articleId, 0L)); // 만약 조회된 데이터가 없으면 0으로 초기화 한다.
        articleLikeCount.increase(); // 조회된 데이터에 대해서 좋아요 수 + 1
        articleLikeCountRepository.save(articleLikeCount); // 조회된 데이터가 없어서 0 으로 초기화 할때, 아직 데이터가 영속되지 않은 상태일 수도 있으므로 명시적으로 호출
    }

    @Transactional
    public void unlikePessimisticLock2(Long articleId, Long userId) {
        articleLikeRepository.findByArticleIdAndUserId(articleId, userId)
                .ifPresent(articleLike -> {
                    articleLikeRepository.delete(articleLike);
                    ArticleLikeCount articleLikeCount = articleLikeCountRepository.findLockedByArticleId(articleId).orElseThrow(); // 좋아요 수 - 1 처리이므로 반드시 데이터가 있어야 한다.
                    articleLikeCount.decrease(); // 조회된 데이터에 대해서 좋아요 수 - 1
                });
    }

    /**
     * 낙관적 락
     */
    @Transactional
    public void likeOptimisticLock(Long articleId, Long userId) {
        articleLikeRepository.save(
                ArticleLike.create(
                        snowflake.nextId(),
                        articleId,
                        userId
                )
        );

        ArticleLikeCount articleLikeCount = articleLikeCountRepository.findById(articleId)
                .orElseGet(() -> ArticleLikeCount.init(articleId, 0L));// 만약 조회된 데이터가 없으면 0으로 초기화 한다.
        articleLikeCount.increase(); // 조회된 데이터에 대해서 좋아요 수 + 1
        articleLikeCountRepository.save(articleLikeCount); // 조회된 데이터가 없어서 0 으로 초기화 할때, 아직 데이터가 영속되지 않은 상태일 수도 있으므로 명시적으로 호출
    }

    @Transactional
    public void unlikeOptimisticLock(Long articleId, Long userId) {
        articleLikeRepository.findByArticleIdAndUserId(articleId, userId)
                .ifPresent(articleLike -> {
                    articleLikeRepository.delete(articleLike);
                    ArticleLikeCount articleLikeCount = articleLikeCountRepository.findById(articleId).orElseThrow();// 좋아요 수 - 1 처리이므로 반드시 데이터가 있어야 한다.
                    articleLikeCount.decrease(); // 조회된 데이터에 대해서 좋아요 수 - 1
                });
    }

    public Long count(Long articleId) {
        return articleLikeCountRepository.findById(articleId)
                .map(ArticleLikeCount::getLikeCount)
                .orElse(0L); // 데이터가 없으면 0 으로 초기화
    }

}
