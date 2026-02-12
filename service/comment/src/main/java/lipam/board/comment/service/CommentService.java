package lipam.board.comment.service;

import lipam.board.comment.entity.Comment;
import lipam.board.comment.repository.CommentRepository;
import lipam.board.comment.service.request.CommentCreateRequest;
import lipam.board.comment.service.response.CommentResponse;
import lipam.board.common.snowflake.Snowflake;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Predicate;

import static java.util.function.Predicate.not;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final Snowflake snowflake = new Snowflake();

    @Transactional
    public CommentResponse create(CommentCreateRequest request) {
        Comment parent = findParent(request);

        Comment comment = commentRepository.save(
                Comment.create(
                        snowflake.nextId(),
                        request.getContent(),
                        parent == null ? null : parent.getCommentId(),
                        request.getArticleId(),
                        request.getWriterId()
                )
        );

        return CommentResponse.from(comment);
    }

    private Comment findParent(CommentCreateRequest request) {
        Long parentCommentId = request.getParentCommentId();
        if (parentCommentId == null) {
            return null;
        }

        return commentRepository.findById(parentCommentId)
                .filter(not(Comment::getDeleted))
                .filter(Comment::isRoot)
                .orElseThrow();
    }

    public CommentResponse read(Long commentId) {
        return CommentResponse.from(
                commentRepository.findById(commentId).orElseThrow()
        );
    }

    @Transactional
    public void delete(Long commentId) {
        commentRepository.findById(commentId)
                .filter(not(Comment::getDeleted)) // 삭제된 댓글인지 = "삭제 표시" 상태인지 검사
                .ifPresent(comment -> { // 댓글이 있으면 삭제
                    if (hasChildren(comment)) {
                        comment.delete();// 하위 댓글이 있다면, "삭제 표시"만
                    } else {
                        delete(comment); // 없으면 실제 삭제 진행
                    }
                });
    }

    private boolean hasChildren(Comment comment) {
        // 하위 댓글이 있는지 판별하는 방법 (본인 댓글 갯수 1 + 하위 댓글 이 하나라도 있는 갯수 1 = 2)
        return commentRepository.countBy(comment.getArticleId(), comment.getCommentId(), 2L) == 2;
    }

    private void delete(Comment comment) {
        commentRepository.delete(comment); // 댓글을 DB 에서 실제 삭제

        if (!comment.isRoot()) { // 삭제 대상이 대댓글이면, 부모도 정리 대상인지 확인
            commentRepository.findById(comment.getParentCommentId()) // 부모 댓글 조회
                    .filter(Comment::getDeleted) // 1. 부모가 이미 "삭제 표시" 상태인 경우만
                    .filter(not(this::hasChildren)) // 2. 부모에게 남은 자식 댓글이 더 없으면
                    .ifPresent(this::delete); // 3. 실제 삭제
        }
    }

}
