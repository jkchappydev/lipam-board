package lipam.board.articleread.api;

import lipam.board.articleread.service.response.ArticleReadPageResponse;
import lipam.board.articleread.service.response.ArticleReadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;

public class ArticleReadApiTest {

    RestClient articleReadRestClient = RestClient.create("http://localhost:9005");
    RestClient articleRestClient = RestClient.create("http://localhost:9000");

    // ArticleQueryModel(Redis Read Model)에 데이터가 있으면
    // → ArticleReadService 내부에서 별도 로그 없이 조회됨
    //
    // 반대로 Read Model 에 데이터가 없으면
    // → 원본(Command) 서버에서 데이터를 가져오며 fetch() 로그가 출력됨
    //
    // DataInitializer 로 생성된 articleId=286722288148418560L 는
    // 이미 ArticleQueryModelRepository 에 존재해야 하므로
    // ArticleReadApplication 로그가 나오면 안 됨
    //
    // 과거 데이터인 279280167282233344L 는 Read Model 에 없어서
    // 원본 서버 fetch 로그가 출력되어야 정상 동작임 (두번째 실행에서는 Read Model 에 있기 때문에 로그가 출력되지 않아야 정상 동작임)
    @Test
    void readTest() {
        ArticleReadResponse response = articleReadRestClient.get()
                .uri("/v1/articles/{articleId}", 279280167282233344L)
                .retrieve()
                .body(ArticleReadResponse.class);

        System.out.println("response = " + response);
    }

    @Test
    void readAllTest() {
        ArticleReadPageResponse response1 = articleReadRestClient.get()
                .uri("/v1/articles?boardId=%s&page=%s&pageSize=%s".formatted(1L, 3000L, 5))
                .retrieve()
                .body(ArticleReadPageResponse.class);

        System.out.println("response1.getArticleCount() = " + response1.getArticleCount());
        for (ArticleReadResponse article : response1.getArticles()) {
            System.out.println("article.getArticleId() = " + article.getArticleId());
        }

        ArticleReadPageResponse response2 = articleRestClient.get()
                .uri("/v1/articles?boardId=%s&page=%s&pageSize=%s".formatted(1L, 3000L, 5))
                .retrieve()
                .body(ArticleReadPageResponse.class);

        System.out.println("response2.getArticleCount() = " + response2.getArticleCount());
        for (ArticleReadResponse article : response2.getArticles()) {
            System.out.println("article.getArticleId() = " + article.getArticleId());
        }

        /** 1번째 페이지 조회 (redis 에 데이터 있음)
         * [ArticleReadService.readAllArticleIds] return redis data.
         * 2026-03-05T02:42:08.422+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640846127181824
         * 2026-03-05T02:42:08.426+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640845846163456
         * 2026-03-05T02:42:08.428+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640845284126720
         * 2026-03-05T02:42:08.431+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640844994719744
         * 2026-03-05T02:42:08.433+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640844566900736
         *
         * article.getArticleId() = 287640846127181824
         * article.getArticleId() = 287640845846163456
         * article.getArticleId() = 287640845284126720
         * article.getArticleId() = 287640844994719744
         * article.getArticleId() = 287640844566900736
         *
         * article.getArticleId() = 287640846127181824
         * article.getArticleId() = 287640845846163456
         * article.getArticleId() = 287640845284126720
         * article.getArticleId() = 287640844994719744
         * article.getArticleId() = 287640844566900736
         */

        /** 3000번째 페이지 조회 (redis 에 데이터 없음)
         * 2026-03-05T02:46:47.803+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.b.a.service.ArticleReadService         : [ArticleReadService.readAllArticleIds] return origin data.
         * 2026-03-05T02:46:47.910+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.b.a.service.ArticleReadService         : [ArticleReadService.fetch] fetch data. articleId=279281791530652120, isPresent=true
         * 2026-03-05T02:46:47.911+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=279281791530652120
         * 2026-03-05T02:46:47.930+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.b.a.service.ArticleReadService         : [ArticleReadService.fetch] fetch data. articleId=279281791530652119, isPresent=true
         * 2026-03-05T02:46:47.931+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=279281791530652119
         * 2026-03-05T02:46:47.943+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.b.a.service.ArticleReadService         : [ArticleReadService.fetch] fetch data. articleId=279281791530652118, isPresent=true
         * 2026-03-05T02:46:47.944+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=279281791530652118
         * 2026-03-05T02:46:47.955+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.b.a.service.ArticleReadService         : [ArticleReadService.fetch] fetch data. articleId=279281791530652117, isPresent=true
         * 2026-03-05T02:46:47.955+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=279281791530652117
         * 2026-03-05T02:46:47.967+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.b.a.service.ArticleReadService         : [ArticleReadService.fetch] fetch data. articleId=279281791530652116, isPresent=true
         * 2026-03-05T02:46:47.967+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-5] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=279281791530652116
         *
         * article.getArticleId() = 279281791530652120
         * article.getArticleId() = 279281791530652119
         * article.getArticleId() = 279281791530652118
         * article.getArticleId() = 279281791530652117
         * article.getArticleId() = 279281791530652116
         *
         * article.getArticleId() = 279281791530652120
         * article.getArticleId() = 279281791530652119
         * article.getArticleId() = 279281791530652118
         * article.getArticleId() = 279281791530652117
         * article.getArticleId() = 279281791530652116
         */
    }

    @Test
    void readAllInfiniteScrollTest() {
        List<ArticleReadResponse> responses1 = articleReadRestClient.get()
                .uri("/v1/articles/infinite-scroll?boardId=%s&pageSize=%s&lastArticleId=%s".formatted(1L, 5L, 287640844566900736L))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ArticleReadResponse>>() {
                });

        for (ArticleReadResponse response : responses1) {
            System.out.println("response = " + response.getArticleId());
        }

        List<ArticleReadResponse> responses2 = articleReadRestClient.get()
                .uri("/v1/articles/infinite-scroll?boardId=%s&pageSize=%s&lastArticleId=%s".formatted(1L, 5L, 287640844566900736L))
                .retrieve()
                .body(new ParameterizedTypeReference<List<ArticleReadResponse>>() {
                });

        for (ArticleReadResponse response : responses2) {
            System.out.println("response = " + response.getArticleId());
        }

        /** /v1/articles/infinite-scroll?boardId=%s&pageSize=%s
         * 2026-03-05T02:53:48.505+09:00  INFO 49765 --- [lipam-board-article-read-service] [io-9005-exec-10] l.b.a.service.ArticleReadService         : [ArticleReadService.readAllInfiniteScrollArticleIds] return redis data.
         * 2026-03-05T02:53:48.506+09:00  INFO 49765 --- [lipam-board-article-read-service] [io-9005-exec-10] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640846127181824
         * 2026-03-05T02:53:48.510+09:00  INFO 49765 --- [lipam-board-article-read-service] [io-9005-exec-10] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640845846163456
         * 2026-03-05T02:53:48.512+09:00  INFO 49765 --- [lipam-board-article-read-service] [io-9005-exec-10] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640845284126720
         * 2026-03-05T02:53:48.514+09:00  INFO 49765 --- [lipam-board-article-read-service] [io-9005-exec-10] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640844994719744
         * 2026-03-05T02:53:48.516+09:00  INFO 49765 --- [lipam-board-article-read-service] [io-9005-exec-10] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640844566900736
         *
         * response = 287640846127181824
         * response = 287640845846163456
         * response = 287640845284126720
         * response = 287640844994719744
         * response = 287640844566900736
         *
         * response = 287640846127181824
         * response = 287640845846163456
         * response = 287640845284126720
         * response = 287640844994719744
         * response = 287640844566900736 <- lastArticleId
         */

        /** /v1/articles/infinite-scroll?boardId=%s&pageSize=%s&lastArticleId=%s
         * 2026-03-05T02:56:51.545+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.b.a.service.ArticleReadService         : [ArticleReadService.readAllInfiniteScrollArticleIds] return redis data.
         * 2026-03-05T02:56:51.546+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640844004864000
         * 2026-03-05T02:56:51.549+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640843539296256
         * 2026-03-05T02:56:51.550+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640843186974720
         * 2026-03-05T02:56:51.552+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640842817875968
         * 2026-03-05T02:56:51.553+09:00  INFO 49765 --- [lipam-board-article-read-service] [nio-9005-exec-3] l.board.articleread.client.ViewClient    : [ViewClient.count] articleId=287640842402639872
         *
         * response = 287640844004864000
         * response = 287640843539296256
         * response = 287640843186974720
         * response = 287640842817875968
         * response = 287640842402639872
         *
         * response = 287640844004864000
         * response = 287640843539296256
         * response = 287640843186974720
         * response = 287640842817875968
         * response = 287640842402639872
         */
    }

}