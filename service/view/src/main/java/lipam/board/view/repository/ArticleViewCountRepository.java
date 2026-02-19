package lipam.board.view.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ArticleViewCountRepository {

    private final StringRedisTemplate redisTemplate;

    // 게시글별 조회수 Redis Key 포맷
    // view::article::{article_id}::view_count
    private static final String KEY_FORMAT = "view::article::%s::view_count";

    // Redis 에서 articleId 로 조회수를 읽어오고 없으면 0을 반환한다.
    public Long read(Long articleId) {
        String result = redisTemplate.opsForValue().get(generateKey(articleId));
        return result == null ? 0L : Long.parseLong(result);
    }

    // Redis 에서 articleId 의 조회수를 1 증가시키고 증가된 값을 반환한다.
    public Long increase(Long articleId) {
        return redisTemplate.opsForValue().increment(generateKey(articleId));
    }

    // articleId 로 조회수 저장/조회에 사용할 Redis Key 를 만든다.
    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }

}
