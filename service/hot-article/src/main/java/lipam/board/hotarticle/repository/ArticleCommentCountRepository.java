package lipam.board.hotarticle.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

// 인기글을 만드는 데 필요한 데이터(예: 댓글 수 등등)는, 인기글 서비스의 책임으로 보고 자체적으로 데이터를 가지고 있는다.
// ArticleCommentCountRepository, ArticleLikeCountRepository, ArticleViewCountRepository
@Repository
@RequiredArgsConstructor
public class ArticleCommentCountRepository {

    private final StringRedisTemplate redisTemplate;

    // hot-article::article::{articleId}::comment-count
    private static final String KEY_FORMAT = "hot-article::article::%s::comment-count";

    public void createOrUpdate(Long articleId, Long commentCount, Duration ttl) {
        redisTemplate.opsForValue().set(generateKey(articleId), String.valueOf(commentCount), ttl);
    }

    public Long read(Long articleId) {
        String result = redisTemplate.opsForValue().get(generateKey(articleId));
        return result == null ? 0L : Long.valueOf(result);
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }

}
