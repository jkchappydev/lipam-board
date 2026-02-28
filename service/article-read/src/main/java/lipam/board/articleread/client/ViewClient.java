package lipam.board.articleread.client;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewClient {

    private RestClient restClient;

    @Value("${endpoints.lipam-board-view-service.url}")
    private String viewServiceUrl;

    @PostConstruct
    public void initRestClient() {
        restClient = RestClient.create(viewServiceUrl);
    }

    // @Cacheable 동작 방식
    // 1. 먼저 Redis 캐시(articleViewCount)에서 key(articleId)로 데이터를 조회한다.
    // 2. 캐시에 값이 있으면 → count() 메서드를 실행하지 않고 캐시 값을 그대로 반환한다.
    // 3. 캐시에 값이 없으면 → count() 메서드를 실제로 실행하여 View 서비스에 요청한다. (아래 로그 출력)
    // 4. 조회 결과를 Redis 캐시에 저장한 뒤 응답한다.
    // 5. 이후 동일 articleId 요청은 TTL(1초) 동안 Redis 캐시 값을 재사용한다.
    @Cacheable(key = "#articleId", value = "articleViewCount")
    public long count(Long articleId) {
        log.info("[ViewClient.count] articleId={}", articleId);
        try {
            return restClient.get()
                    .uri("/v1/article-views/articles/{articleId}/count", articleId)
                    .retrieve()
                    .body(Long.class);
        } catch (Exception e) {
            log.error("[ViewClient.count] articleId={}]", articleId, e);
            return 0;
        }
    }

}
