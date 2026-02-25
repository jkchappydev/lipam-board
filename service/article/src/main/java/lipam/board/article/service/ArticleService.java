package lipam.board.article.service;

import lipam.board.article.entity.Article;
import lipam.board.article.entity.BoardArticleCount;
import lipam.board.article.repository.ArticleRepository;
import lipam.board.article.repository.BoardArticleCountRepository;
import lipam.board.article.service.request.ArticleCreateRequest;
import lipam.board.article.service.request.ArticleUpdateRequest;
import lipam.board.article.service.response.ArticlePageResponse;
import lipam.board.article.service.response.ArticleResponse;
import lipam.board.common.event.EventType;
import lipam.board.common.event.payload.ArticleCreatedEventPayload;
import lipam.board.common.event.payload.ArticleDeletedEventPayload;
import lipam.board.common.event.payload.ArticleUpdatedEventPayload;
import lipam.board.common.outboxmessagerelay.OutboxEventPublisher;
import lipam.board.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final Snowflake snowflake = new Snowflake();
    private final ArticleRepository articleRepository;
    private final BoardArticleCountRepository boardArticleCountRepository;

    // 게시글 생성/수정/삭제 시점에 OutboxEventPublisher 로 이벤트를 발행한다.
    // - 트랜잭션 커밋 "전"  : MessageRelay.createOutbox(...)가 받아서 outbox 테이블에 저장(save)된다.
    // - 트랜잭션 커밋 "후"  : MessageRelay.publishEvent(...)가 outbox 를 Kafka 로 전송하고,
    //                      전송 성공 시 outbox 테이블에서 삭제(delete)된다.
    // 즉, DB 작업과 이벤트 저장을 먼저 묶어두고(커밋 전), 커밋이 끝난 뒤에 Kafka 전송을 안전하게 처리한다.
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public ArticleResponse create(ArticleCreateRequest request) {
        Article article = articleRepository.save(
                Article.create(
                        snowflake.nextId(),
                        request.getTitle(),
                        request.getContent(),
                        request.getBoardId(),
                        request.getWriterId()
                )
        );

        // 게시글이 생성될 때, 전체 게시글 수 + 1
        int result = boardArticleCountRepository.increase(request.getBoardId());
        if (result == 0) {
            boardArticleCountRepository.save(
                    BoardArticleCount.init(
                            request.getBoardId(),
                            1L
                    )
            );
        }

        // 게시글 생성 이벤트 발행
        // 게시글 생성 시 Outbox에 이벤트 저장 → 트랜잭션 커밋 후 Kafka 전송 → boardId 기준 샤드 라우팅으로 게시글 단위 순서 보장
        // - type: ARTICLE_CREATED
        // - payload: 생성된 게시글 정보 + 게시판의 전체 게시글 수(boardArticleCount)
        // - shardKey: boardId 기준으로 샤드 라우팅 (같은 boardId는 같은 샤드로 모이게)
        outboxEventPublisher.publish(
                EventType.ARTICLE_CREATED,
                ArticleCreatedEventPayload.builder()
                        .articleId(article.getArticleId())
                        .title(article.getTitle())
                        .content(article.getContent())
                        .boardId(article.getBoardId())
                        .writerId(article.getWriterId())
                        .createdAt(article.getCreatedAt())
                        .modifiedAt(article.getModifiedAt())
                        .boardArticleCount(count(article.getBoardId()))
                        .build(),
                article.getBoardId()
        );

        return ArticleResponse.from(article);
    }

    @Transactional
    public ArticleResponse update(Long articleId, ArticleUpdateRequest request) {
        Article article = articleRepository.findById(articleId).orElseThrow();
        article.update(request.getTitle(), request.getContent());

        // 게시글 수정 이벤트 발행
        // 게시글 수정 시 Outbox에 이벤트 저장 → 트랜잭션 커밋 후 Kafka 전송 → boardId 기준 샤드 라우팅으로 게시글 단위 순서 보장
        // - type: ARTICLE_UPDATED
        // - payload: 수정된 게시글 정보
        // - shardKey: boardId 기준 샤드 라우팅
        outboxEventPublisher.publish(
                EventType.ARTICLE_UPDATED,
                ArticleUpdatedEventPayload.builder()
                        .articleId(article.getArticleId())
                        .title(article.getTitle())
                        .content(article.getContent())
                        .boardId(article.getBoardId())
                        .writerId(article.getWriterId())
                        .createdAt(article.getCreatedAt())
                        .modifiedAt(article.getModifiedAt())
                        .build(),
                article.getBoardId()
        );

        return ArticleResponse.from(article);
    }

    public ArticleResponse read(Long articleId) {
        return ArticleResponse.from(articleRepository.findById(articleId).orElseThrow());
    }

    @Transactional
    public void delete(Long articleId) {
        Article article = articleRepository.findById(articleId).orElseThrow();
        articleRepository.delete(article); // deleteById : ID 기반 삭제 -> delete : 엔티티를 명시적으로 지정해서 삭제
        // 게시글이 생성될 때, 전체 게시글 수 - 1
        boardArticleCountRepository.decrease(article.getBoardId());

        // 게시글 삭제 이벤트 발행
        // 게시글 삭제 시 Outbox에 이벤트 저장 → 트랜잭션 커밋 후 Kafka 전송 → boardId 기준 샤드 라우팅으로 게시글 단위 순서 보장
        // - type: ARTICLE_DELETED
        // - payload: 삭제된 게시글 정보(삭제 이후 다른 서비스들이 정리/반영할 때 사용)
        // - shardKey: boardId 기준 샤드 라우팅
        outboxEventPublisher.publish(
                EventType.ARTICLE_DELETED,
                ArticleDeletedEventPayload.builder()
                        .articleId(article.getArticleId())
                        .title(article.getTitle())
                        .content(article.getContent())
                        .boardId(article.getBoardId())
                        .writerId(article.getWriterId())
                        .createdAt(article.getCreatedAt())
                        .modifiedAt(article.getModifiedAt())
                        .build(),
                article.getBoardId()
        );
    }

    public ArticlePageResponse readAll(Long boardId, Long page, Long pageSize) {
        return ArticlePageResponse.of(
                articleRepository.findAll(boardId, (page - 1) * pageSize, pageSize).stream()
                        .map(ArticleResponse::from)
                        .toList(),
                articleRepository.count(
                        boardId,
                        PageLimitCalculator.calculatePageLimit(page, pageSize, 10L)
                )
        );
    }

    public List<ArticleResponse> readAllInfiniteScroll(Long boardId, Long pageSize, Long lastArticleId) {
        List<Article> articles = lastArticleId == null ?
                articleRepository.findAllInfiniteScroll(boardId, pageSize) :
                articleRepository.findAllInfiniteScroll(boardId, pageSize, lastArticleId);

        return articles.stream().map(ArticleResponse::from).toList();
    }

    public Long count(Long boardId) {
        return boardArticleCountRepository.findById(boardId)
                .map(BoardArticleCount::getArticleCount)
                .orElse(0L);
    }

}
