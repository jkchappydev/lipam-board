package lipam.board.articleread.repository;

import lipam.board.common.dataserializer.DataSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@Repository
@RequiredArgsConstructor
public class ArticleQueryModelRepository {

    private final StringRedisTemplate redisTemplate;

    // article-read::article::{articleId}
    private static final String KEY_FORMAT = "article-read::article::%s";

    public void create(ArticleQueryModel articleQueryModel, Duration ttl) {
        redisTemplate.opsForValue()
                .set(generateKey(articleQueryModel), DataSerializer.serialize(articleQueryModel), ttl);
    }

    public void update(ArticleQueryModel articleQueryModel) { // 업데이트는 TTL 필요없음 (업데이트 때 TTL 을 다시 걸면 만료 시간이 리셋될 수 있어서 의도와 다르게 "영구 생존"이 될 수 있다.)
        redisTemplate.opsForValue()
                .setIfPresent(generateKey(articleQueryModel), DataSerializer.serialize(articleQueryModel)); // 키에 대해서 데이터가 있을 때만 업데이트를 수행
    }

    public void delete(Long articleId) {
        // 게시글 삭제 이벤트를 받으면 Read 모델도 삭제할 수 있다.
        redisTemplate.delete(generateKey(articleId));
    }

    public Optional<ArticleQueryModel> read(Long articleId) {
        // Redis 에서 조회 → JSON 이면 역직렬화해서 QueryModel 로 변환한다.
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(generateKey(articleId))
        ).map(json -> DataSerializer.deserialize(json, ArticleQueryModel.class));
    }

    private String generateKey(ArticleQueryModel articleQueryModel) {
        return generateKey(articleQueryModel.getArticleId());
    }

    private String generateKey(Long articleId) {
        return KEY_FORMAT.formatted(articleId);
    }

    public Map<Long, ArticleQueryModel> readAll(List<Long> articleIds) {
        List<String> keyList = articleIds.stream().map(this::generateKey).toList();
        return redisTemplate.opsForValue().multiGet(keyList).stream() // multiGet() : 여러개의 데이터를 한번에 조회
                .filter(Objects::nonNull)
                .map(json -> DataSerializer.deserialize(json, ArticleQueryModel.class))
                .collect(toMap(ArticleQueryModel::getArticleId, Function.identity()));
    }

}
