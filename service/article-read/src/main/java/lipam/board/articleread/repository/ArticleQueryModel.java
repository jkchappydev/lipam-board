package lipam.board.articleread.repository;

import lipam.board.articleread.client.ArticleClient;
import lipam.board.common.event.payload.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ArticleQueryModel {

    // Read 서비스가 Redi s에 들고 있을 "조회 전용 모델"
    // (쓰기 모델 DB를 직접 못 보니까, 조회는 이 모델로 빠르게 처리)
    private Long articleId;
    private String title;
    private String content;
    private Long boardId;
    private Long writerId;
    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
    private Long articleCommentCount;
    private Long articleLikeCount;

    // 이벤트를 받으면 Read 모델도 같이 업데이트해야 한다.
    // (게시글 생성/수정, 댓글 생성/삭제, 좋아요 생성/삭제 등)
    // CQRS 핵심: Read Model 정합성은 "이벤트"로 맞춘다. -> 게시글 생성/수정 + 댓글/좋아요 변화 이벤트를 받아서 Read Model 을 갱신

    // 생성(이벤트 기반)
    // - 게시글 생성 이벤트(ArticleCreatedEventPayload)를 받으면 Read 모델을 최초로 만들어서 Redis 에 저장할 수 있다.
    public static ArticleQueryModel create(ArticleCreatedEventPayload payload) {
        ArticleQueryModel articleQueryModel = new ArticleQueryModel();
        articleQueryModel.articleId = payload.getArticleId();
        articleQueryModel.title = payload.getTitle();
        articleQueryModel.content = payload.getContent();
        articleQueryModel.boardId = payload.getBoardId();
        articleQueryModel.writerId = payload.getWriterId();
        articleQueryModel.createdAt = payload.getCreatedAt();
        articleQueryModel.modifiedAt = payload.getModifiedAt();
        articleQueryModel.articleCommentCount = 0L;
        articleQueryModel.articleLikeCount = 0L;

        return articleQueryModel;
    }

    // 생성(Command 조회 기반)
    // - Redis miss 가 났을 때 Command 서버에서 원본 데이터(ArticleClient.ArticleResponse)를 가져와 Read 모델을 만들 수 있다.
    // - 이때 댓글 수/좋아요 수 같은 집계값은 별도 조회 결과를 합쳐서 만들 수 있다.
    public static ArticleQueryModel create(ArticleClient.ArticleResponse article, Long commentCount, Long likeCount) {
        ArticleQueryModel articleQueryModel = new ArticleQueryModel();
        articleQueryModel.articleId = article.getArticleId();
        articleQueryModel.title = article.getTitle();
        articleQueryModel.content = article.getContent();
        articleQueryModel.boardId = article.getBoardId();
        articleQueryModel.writerId = article.getWriterId();
        articleQueryModel.createdAt = article.getCreatedAt();
        articleQueryModel.modifiedAt = article.getModifiedAt();
        articleQueryModel.articleCommentCount = commentCount;
        articleQueryModel.articleLikeCount = likeCount;

        return articleQueryModel;
    }

    // 댓글 생성 이벤트(CommentCreatedEventPayload)를 받으면 댓글 수를 이벤트 기준 값으로 동기화할 수 있다.
    public void updateBy(CommentCreatedEventPayload payload) {
        this.articleCommentCount = payload.getArticleCommentCount();
    }

    // 댓글 삭제 이벤트(CommentDeletedEventPayload)를 받으면 댓글 수를 이벤트 기준 값으로 동기화할 수 있다.
    public void updateBy(CommentDeletedEventPayload payload) {
        this.articleCommentCount = payload.getArticleCommentCount();
    }

    // 좋아요 생성 이벤트(ArticleLikedEventPayload)를 받으면 좋아요 수를 이벤트 기준 값으로 동기화할 수 있다.
    public void updateBy(ArticleLikedEventPayload payload) {
        this.articleLikeCount  = payload.getArticleLikeCount();
    }

    // 좋아요 취소 이벤트(ArticleUnlikedEventPayload)를 받으면 좋아요 수를 이벤트 기준 값으로 동기화할 수 있다.
    public void updateBy(ArticleUnlikedEventPayload payload) {
        this.articleLikeCount  = payload.getArticleLikeCount();
    }

    // 게시글 수정 이벤트(ArticleUpdatedEventPayload)를 받으면 본문/제목 같은 원본 필드를 갱신할 수 있다.
    public void updateBy(ArticleUpdatedEventPayload payload) {
        this.title = payload.getTitle();
        this.content = payload.getContent();
        this.boardId = payload.getBoardId();
        this.writerId = payload.getWriterId();
        this.createdAt = payload.getCreatedAt();
        this.modifiedAt = payload.getModifiedAt();
    }

}
