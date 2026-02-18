package lipam.board.comment.service;

import lipam.board.comment.entity.ArticleCommentCount;
import lipam.board.comment.entity.CommentPath;
import lipam.board.comment.entity.CommentV2;
import lipam.board.comment.repository.ArticleCommentCountRepository;
import lipam.board.comment.repository.CommentRepositoryV2;
import lipam.board.comment.service.request.CommentCreateRequestV2;
import lipam.board.comment.service.response.CommentPageResponse;
import lipam.board.comment.service.response.CommentResponse;
import lipam.board.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static java.util.function.Predicate.not;

@Service
@RequiredArgsConstructor
public class CommentServiceV2 {

    private final Snowflake snowflake = new Snowflake();
    private final CommentRepositoryV2 commentRepository;
    private final ArticleCommentCountRepository articleCommentCountRepository;

    @Transactional
    public CommentResponse create(CommentCreateRequestV2 request) {
        CommentV2 parent = findParent(request);
        CommentPath parentCommentPath = parent == null ? CommentPath.create("") : parent.getCommentPath();// parent == null 이면 1뎁스

        CommentV2 comment = commentRepository.save(
                CommentV2.create(
                        snowflake.nextId(),
                        request.getContent(),
                        request.getArticleId(),
                        request.getWriterId(),
                        parentCommentPath.createChileCommentPath(
                                commentRepository.findDescendantsTopPath(request.getArticleId(), parentCommentPath.getPath())
                                        .orElse(null) // null 이면 MIN_CHUNK 를 바로 붙힘
                        )
                )
        );

        // 댓글이 생성될 때, 전체 댓글 수 + 1
        int result = articleCommentCountRepository.increase(request.getArticleId());
        if (result == 0) {
            articleCommentCountRepository.save(
                    ArticleCommentCount.init(request.getArticleId(), 1L)
            );
        }

        return CommentResponse.from(comment);
    }

    private CommentV2 findParent(CommentCreateRequestV2 request) {
        String parentPath = request.getParentPath();
        if (parentPath == null) {
            return null;
        }
        return commentRepository.findByPath(parentPath)
                .filter(not(CommentV2::getDeleted))
                .orElseThrow();
    }

    public CommentResponse read(Long commentId) {
        return CommentResponse.from(commentRepository.findById(commentId).orElseThrow());
    }

    @Transactional
    public void delete(Long commentId) {
        commentRepository.findById(commentId)
                .filter(not(CommentV2::getDeleted))
                .ifPresent(comment -> {
                    if (hasChildren(comment)) {
                        comment.delete();
                    } else {
                        delete(comment);
                    }
                });
    }

    private boolean hasChildren(CommentV2 comment) {
        return commentRepository.findDescendantsTopPath(
                comment.getArticleId(),
                comment.getCommentPath().getPath()
        ).isPresent();
    }

    private void delete(CommentV2 comment) {
        commentRepository.delete(comment); // 댓글을 DB 에서 실제 삭제
        articleCommentCountRepository.decrease(comment.getArticleId()); // 댓글이 실제 삭제될 때, 전체 댓글 수 - 1
        if (!comment.isRoot()) { // 삭제 대상이 대댓글이면, 부모도 정리 대상인지 확인
            commentRepository.findByPath(comment.getCommentPath().getPath()) // 부모 댓글 조회
                    .filter(CommentV2::getDeleted) // 1. 부모가 이미 "삭제 표시" 상태인 경우만
                    .filter(not(this::hasChildren)) // 2. 부모에게 남은 자식 댓글이 더 없으면
                    .ifPresent(this::delete); // 3. 실제 삭제
        }
    }

    public CommentPageResponse readAll(
            Long articleId,
            Long page,
            Long pageSize
    ) {
        return CommentPageResponse.of(
                commentRepository.findAll(articleId, pageSize, (page - 1) * pageSize).stream()
                        .map(CommentResponse::from)
                        .toList(),
                commentRepository.count(articleId, PageLimitCalculator.calculatePageLimit(page, pageSize, 10L))
                // count(articleId) // 예전엔 이동한 페이지에 대한 댓글 수를 일부만 카운트했지만, 이제 댓글 수를 실시간으로 바로 조회하니까 count(articleId)로도 바꿀 수 있다.
        );
    }

    public List<CommentResponse> readAllInfiniteScroll(Long articleId, String lastPath, Long pageSize) {
        List<CommentV2> comments = lastPath == null ?
                commentRepository.findAllInfiniteScroll(articleId, pageSize) :
                commentRepository.findAllInfiniteScroll(articleId, lastPath, pageSize);

        return comments.stream()
                .map(CommentResponse::from)
                .toList();
    }

    public Long count(Long articleId) {
        return articleCommentCountRepository.findById(articleId)
                .map(ArticleCommentCount::getCommentCount)
                .orElse(0L);
    }

}
