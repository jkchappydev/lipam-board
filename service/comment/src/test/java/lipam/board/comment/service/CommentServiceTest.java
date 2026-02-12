package lipam.board.comment.service;

import lipam.board.comment.entity.Comment;
import lipam.board.comment.repository.CommentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @InjectMocks
    CommentService commentService;

    @Mock
    CommentRepository commentRepository;

    @Test
    @DisplayName("삭제 대상인 상위 댓글에 하위 댓글이 있으면, 상위 댓글은 삭제 표시만 한다.")
    void deleteShouldMarkDeletedIfHasChildren() {
        // given
        Long articleId = 1L;
        Long commentId = 2L;

        Comment comment = createComment(articleId, commentId);

        // 삭제 대상 댓글이 존재하는 상황
        BDDMockito.given(commentRepository.findById(commentId))
                .willReturn(Optional.of(comment)); // 의 결과가
        // 삭제 대상 댓글에 하위 댓글이 있는 상황(count == 2)
        BDDMockito.given(commentRepository.countBy(articleId, commentId, 2L))
                .willReturn(2L);

        // when
        commentService.delete(commentId);

        // then
        Mockito.verify(comment).delete();
    }

    private Comment createComment(Long articleId, Long commentId) {
        Comment comment = Mockito.mock(Comment.class);
        BDDMockito.given(comment.getArticleId())
                .willReturn(articleId);
        BDDMockito.given(comment.getCommentId())
                .willReturn(commentId);

        return comment;
    }

    private Comment createComment(Long articleId, Long commentId, Long parentCommentId) {
        Comment comment = createComment(articleId, commentId);
        BDDMockito.given(comment.getParentCommentId()).willReturn(parentCommentId);

        return comment;
    }

    @Test
    @DisplayName("삭제 대상이 하위 댓글이고, 상위 댓글이 삭제 상태가 아니면, 하위 댓글만 실제 삭제한다.")
    void deleteShouldDeleteChildOnlyIfNotDeletedParent() {
        // given
        Long articleId = 1L;
        Long commentId = 2L;
        Long parentCommentId = 1L;

        Comment comment = createComment(articleId, commentId, parentCommentId);
        // 삭제 대상이 상위 댓글이 아닌 "하위 댓글"인 상황
        BDDMockito.given(comment.isRoot())
                .willReturn(false);

        Comment parentComment = Mockito.mock(Comment.class);
        // 상위 댓글이 삭제 상태가 아닌 상황(deleted = false)
        BDDMockito.given(parentComment.getDeleted())
                .willReturn(false);
        // 삭제 대상 하위 댓글이 존재하는 상황
        BDDMockito.given(commentRepository.findById(commentId))
                .willReturn(Optional.of(comment));
        // 상위 댓글에 남아있는 하위 댓글이 더 없는 상황(count == 1)
        BDDMockito.given(commentRepository.countBy(articleId, commentId, 2L))
                .willReturn(1L);
        // 상위 댓글이 조회되는 상황
        BDDMockito.given(commentRepository.findById(parentCommentId))
                .willReturn(Optional.of(parentComment));

        // when
        commentService.delete(commentId);

        // then
        Mockito.verify(commentRepository).delete(comment);
        Mockito.verify(commentRepository, Mockito.never()).delete(parentComment);
    }

    @Test
    @DisplayName("삭제 대상인 하위 댓글을 실제 삭제한 뒤, 상위 댓글이 삭제 상태이고 하위 댓글이 더 없으면, 상위 댓글도 재귀적으로 실제 삭제한다.")
    void deleteShouldDeleteAllRecursivelyIfDeletedParent() {
        // given
        Long articleId = 1L;
        Long commentId = 2L;
        Long parentCommentId = 1L;

        Comment comment = createComment(articleId, commentId, parentCommentId);
        // 삭제 대상이 상위 댓글이 아닌 "하위 댓글"인 상황
        BDDMockito.given(comment.isRoot())
                .willReturn(false);

        Comment parentComment = createComment(articleId, parentCommentId);
        // 상위 댓글인 상황
        BDDMockito.given(parentComment.isRoot())
                .willReturn(true);
        // 상위 댓글이 이미 삭제 표시된 상태인 상황(deleted = true)
        BDDMockito.given(parentComment.getDeleted())
                .willReturn(true);

        // 삭제 대상 하위 댓글이 존재하는 상황
        BDDMockito.given(commentRepository.findById(commentId))
                .willReturn(Optional.of(comment));
        // 상위 댓글에 남아있는 하위 댓글이 더 없는 상황(count == 1)
        BDDMockito.given(commentRepository.countBy(articleId, commentId, 2L))
                .willReturn(1L);

        // 상위 댓글이 조회되는 상황(연쇄 삭제 대상)
        BDDMockito.given(commentRepository.findById(parentCommentId))
                .willReturn(Optional.of(parentComment));
        // 상위 댓글에 남은 하위 댓글이 더 없는 상황(count == 1)
        BDDMockito.given(commentRepository.countBy(articleId, parentCommentId, 2L))
                .willReturn(1L);

        // when
        commentService.delete(commentId);

        // then
        Mockito.verify(commentRepository).delete(comment);
        Mockito.verify(commentRepository).delete(parentComment);
    }

}