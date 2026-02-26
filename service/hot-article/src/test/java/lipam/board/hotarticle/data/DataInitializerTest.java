package lipam.board.hotarticle.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.random.RandomGenerator;

// article/comment/like/view 서비스에 데이터 생성 요청
public class DataInitializerTest {

    RestClient articleServiceClient = RestClient.create("http://localhost:9000");
    RestClient commentServiceClient = RestClient.create("http://localhost:9001");
    RestClient likeServiceClient = RestClient.create("http://localhost:9002");
    RestClient viewServiceClient = RestClient.create("http://localhost:9003");

    @Test
    void initialize() {
        // 게시글 30개 생성하면서, 각 게시글에 댓글/좋아요/조회수를 랜덤으로 추가
         for (int i = 0; i < 30; i++) {
             Long articleId = createArticle(); // 게시글 생성 후 articleId 확보

             // 게시글마다 랜덤으로 생성할 이벤트 수(댓글/좋아요/조회수)
             long commentCount = RandomGenerator.getDefault().nextLong(10); // 0~9개
             long likeCount = RandomGenerator.getDefault().nextLong(10);    // 0~9개
             long viewCount = RandomGenerator.getDefault().nextLong(200);   // 0~199개

             // 확보한 articleId에 대해 댓글/좋아요/조회수 요청을 반복 호출
             createComment(articleId, commentCount);
             like(articleId, likeCount);
             view(articleId, viewCount);
         }
    }

    Long createArticle() {
        // 게시글 생성 API 호출 → 응답에서 articleId 꺼내 반환
        return articleServiceClient.post()
                .uri("/v1/articles")
                .body(new ArticleCreateRequest("title", "content", 1L, 1L))
                .retrieve()
                .body(ArticleResponse.class)
                .getArticleId();
    }

    @Getter
    @AllArgsConstructor
    static class ArticleCreateRequest {
        private String title;
        private String content;
        private Long writerId;
        private Long boardId;
    }

    @Getter
    static class ArticleResponse {
        private Long articleId;
    }

    void createComment(Long articleId, long commentCount) {
        // commentCount 만큼 댓글 생성 API 반복 호출
        while(commentCount-- > 0) {
            commentServiceClient.post()
                    .uri("/v2/comments")
                    .body(new CommentCreateRequest(articleId, "content", 1L))
                    .retrieve();
        }
    }

    @Getter
    @AllArgsConstructor
    static class CommentCreateRequest {
        private Long articleId;
        private String content;
        private Long writerId;
    }

    void like(Long articleId, long likeCount) {
        // likeCount 만큼 좋아요 API 반복 호출
        while(likeCount-- > 0) {
            likeServiceClient.post()
                    .uri("/v1/article-likes/articles/{articleId}/users/{userId}/pessimistic-lock-1", articleId, likeCount) // 여기서 userId 에 likeCount 대충 넣은 이유는, userId 는 유니크여야 하기 때문
                    .retrieve();
        }
    }

    void view(Long articleId, long viewCount) {
        // viewCount 만큼 조회수 API 반복 호출
        while(viewCount-- > 0) {
            viewServiceClient.post()
                    .uri("/v1/article-views/articles/{articleId}/users/{userId}", articleId, viewCount)  // 여기서 userId 에 viewCount 대충 넣은 이유는, userId 는 유니크여야 하기 때문
                    .retrieve();
        }
    }

}
