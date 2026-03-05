package lipam.board.articleread.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// cache 갱신 요청에 대해서는 분산 락을 잡아서 한 건의 요청만 처리되도록 함
@Component
@RequiredArgsConstructor
public class OptimizedCacheLockProvider {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "optimized-cache-lock::";
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    // lock 을 잡는 메서드
    public boolean lock(String key) {
        return redisTemplate.opsForValue().setIfAbsent(
                generateLockKey(key),
                "",
                LOCK_TTL
        );
    }

    // lock 을 해제하는 메서드
    public void unlock(String key) {
        redisTemplate.delete(generateLockKey(key));
    }

    private String generateLockKey(String key) {
        return KEY_PREFIX + key;
    }

}
