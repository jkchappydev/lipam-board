package lipam.board.comment.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommentPathTest {

    @Test
    void createChildCommentTest() {
        // 최초생성 (1뎁스)
        // 00000 <- 생성
        createChildCommentTest(CommentPath.create(""), null, "00000");

        // 하위댓글 최초생성 (2뎁스)
        // 00000
        //      00000 <- 생성
        createChildCommentTest(CommentPath.create("00000"), null, "0000000000");

        // 1뎁스 추가생성
        // 00000
        // 00001 <- 생성
        createChildCommentTest(CommentPath.create(""), "00000", "00001");

        // 0000z
        //      abcdz
        //          zzzzz
        //              zzzzz
        //      abce0 <- 생성
        createChildCommentTest(CommentPath.create("0000z"), "0000zabcdzzzzzzzzzzz", "0000zabce0");
    }

    void createChildCommentTest(CommentPath commentPath, String descendantsTopPath, String expectedChildPath) {
        CommentPath chileCommentPath = commentPath.createChileCommentPath(descendantsTopPath);
        assertThat(chileCommentPath.getPath()).isEqualTo(expectedChildPath);
    }

    @Test
    void createChildCommentPathIfMaxDepthTest() { // 5뎁스를 초과하는 하위댓글 생성 불가능
        assertThatThrownBy(() ->
                CommentPath.create("zzzzz".repeat(5)).createChileCommentPath(null)
        ).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createChildCommentPathIfChunkOverflowTest() { // 특정 뎁스의 마지막 댓글이 "zzzzz" 이면, 동일한 뎁스에 댓글 추가생성 불가능
        // given
        CommentPath commentPath = CommentPath.create("");

        // when, then
        assertThatThrownBy(() ->
                commentPath.createChileCommentPath("zzzzz")
        ).isInstanceOf(IllegalStateException.class);
    }

}