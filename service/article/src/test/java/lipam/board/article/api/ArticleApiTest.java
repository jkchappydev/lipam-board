package lipam.board.article.api;

import lipam.board.article.service.response.ArticlePageResponse;
import lipam.board.article.service.response.ArticleResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

public class ArticleApiTest {

    RestClient restClient = RestClient.create("http://localhost:9000");

    @Test
    void createTest() {
        ArticleResponse response = create(new ArticleCreateRequest(
                "hi", "my content", 1L, 1L
        ));
        System.out.println("response: " + response);
    }

    ArticleResponse create(ArticleCreateRequest request) {
        return  restClient.post() // http 메서드 타입
                .uri("/v1/articles") // 요청 url
                .body(request) // 요청 body
                .retrieve() // 실행
                .body(ArticleResponse.class); // 응답 body
    }

    @Test
    void readTest() {
        ArticleResponse response = read(279113948058034176L);
        System.out.println("response: " + response);
    }

    ArticleResponse read(Long articleId) {
        return restClient.get()
                .uri("/v1/articles/{articleId}", articleId)
                .retrieve()
                .body(ArticleResponse.class);
    }

    @Test
    void updateTest() {
        update(279067628687347712L);
        ArticleResponse response = read(279067628687347712L);
        System.out.println("response: " + response);
    }

    void update(Long articleId) {
        restClient.put()
                .uri("/v1/articles/{articleId}", articleId)
                .body(new ArticleUpdateRequest("hi 2", "my content2"))
                .retrieve()
                .body(ArticleResponse.class);
    }

    @Test
    void deleteTest() {
        restClient.delete()
                .uri("/v1/articles/{articleId}", 279067628687347712L)
                .retrieve();
    }

    @Test
    void readAllTest() {
        ArticlePageResponse response = restClient.get()
                .uri("/v1/articles?boardId=1&pageSize=30&page=50000")
                .retrieve()
                .body(ArticlePageResponse.class);

        System.out.println("response.getArticleCount(): " + response.getArticleCount());
        for (ArticleResponse article : response.getArticles()) {
            System.out.println("articleId: " + article.getArticleId());
        }
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
    @AllArgsConstructor
    static class ArticleUpdateRequest {
        private String title;
        private String content;
    }

}
