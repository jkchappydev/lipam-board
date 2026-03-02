package lipam.board.articleread.api;

import lipam.board.articleread.service.response.ArticleReadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

public class ArticleReadApiTest {

    RestClient restClient = RestClient.create("http://localhost:9005");

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
        ArticleReadResponse response = restClient.get()
                .uri("/v1/articles/{articleId}", 279280167282233344L)
                .retrieve()
                .body(ArticleReadResponse.class);

        System.out.println("response = " + response);
    }
}