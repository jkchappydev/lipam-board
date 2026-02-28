package lipam.board.articleread.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
class ViewClientTest {

    @Autowired
    ViewClient viewClient;

    // 동일 articleId(1L)를 여러 번 호출하여 @Cacheable 동작 확인
    @Test
    void readCacheableTest() throws InterruptedException {
        // 똑같은거 3번 호출
        viewClient.count(1L); // Redis 캐시에 데이터가 없으므로 실제 View 서비스 API 호출 발생 -> 로그 출력
        viewClient.count(1L); // Redis 캐시에 값이 있으므로 메서드 실행 없이 캐시 값 반환 -> 로그 미출력
        viewClient.count(1L); // TTL(1초) 이내 -> 캐시 값 그대로 반환 -> 로그 미출력

        // 캐시 TTL(1초)보다 길게 대기 -> 3초
        TimeUnit.SECONDS.sleep(3);
        viewClient.count(1L); // Redis 캐시가 사라졌으므로 다시 실제 API 호출 발생 -> 로그 출력
    }

}