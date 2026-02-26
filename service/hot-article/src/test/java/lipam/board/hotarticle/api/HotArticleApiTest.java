package lipam.board.hotarticle.api;

import lipam.board.hotarticle.service.response.HotArticleResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.util.List;

public class HotArticleApiTest {

    RestClient restClient = RestClient.create("http://localhost:9004");

    @Test
    void readAllTest() {
        // 날짜(yyyyMMdd) 기준으로 인기글 목록 조회 API 호출
        List<HotArticleResponse> responses = restClient.get()
                .uri("/v1/hot-articles/articles/date/{dateStr}", "20260226")
                .retrieve()
                .body(new ParameterizedTypeReference<List<HotArticleResponse>>() {
                }); // List 같은 제네릭 타입은 ParameterizedTypeReference 로 감싸야 한다.

        for (HotArticleResponse response : responses) {
            System.out.println("response = " + response);
        }

    }

}
