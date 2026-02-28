package lipam.board.articleread.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching // Spring Boot 캐시 기능 활성화
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Redis 를 캐시 저장소로 쓰는 CacheManager 생성
        return RedisCacheManager.builder(connectionFactory)
                .withInitialCacheConfigurations(
                        // 캐시에 대한 설정
                        // 캐시 이름별로 TTL(만료시간)을 다르게 줄 수 있다.
                        // 여기서는 "articleViewCount" 캐시에만 1초 TTL 을 적용한다.
                        // 조회수는 값이 자주 바뀌는 데이터라 캐시를 오래 잡으면 최신 값 반영이 늦어질 수 있어서,
                        // 아주 짧게(1초)만 캐시해서 순간적인 중복 조회/호출만 줄이는 용도로 쓴다.
                        Map.of(
                                "articleViewCount", RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(1))
                        )
                ).build();
    }

}
