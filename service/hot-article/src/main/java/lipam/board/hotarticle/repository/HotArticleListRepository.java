package lipam.board.hotarticle.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class HotArticleListRepository {

    private final StringRedisTemplate redisTemplate;

    // hot-article::list::{yyyyMMdd}
    private static final String KEY_FORMAT = "hot-article::list::%s";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 인기글을 실시간으로 만드는 메서드
    public void add(Long articleId, LocalDateTime time, Long score, Long limit, Duration ttl) {
        // executePipelined(): Redis 명령 여러 개(여기서는 zAdd, zRemRange, expire)를 파이프라인으로 묶어서 한 번에 보내 네트워크 왕복을 줄이고 처리 속도를 올린다.
        redisTemplate.executePipelined((RedisCallback<?>) action -> {
            // StringRedisConnection: Redis 에 문자열 기반 명령(zAdd/expire 등)을 바로 날리기 위해 커넥션을 캐스팅한다.
            StringRedisConnection conn = (StringRedisConnection) action;
            String key = generateKey(time);
            // zAdd(): ZSET 에 (member=articleId, score=점수)로 추가/갱신해서 랭킹 점수를 반영한다.
            conn.zAdd(key, score, String.valueOf(articleId));
            // zRemRange(): 낮은 점수 구간(0 ~ 끝에서 limit 초과분)을 잘라서 상위 limit 개만 남긴다.
            conn.zRemRange(key, 0, -limit - 1);
            // expire(): 이 ZSET 키가 ttl 지나면 자동으로 날아가게 만료 시간을 건다.
            conn.expire(key, ttl.toSeconds());

            // 파이프라인 콜백은 반환값을 안 쓰니까 null 로 끝낸다.
            return null;
        });
    }

    public void remove(Long articleId, LocalDateTime time) {
        redisTemplate.opsForZSet().remove(generateKey(time), String.valueOf(articleId));
    }

    private String generateKey(LocalDateTime time) {
        return generateKey(TIME_FORMATTER.format(time));
    }

    private String generateKey(String dateStr) {
        return KEY_FORMAT.format(dateStr);
    }

    public List<Long> readAll(String dateStr) {
        return redisTemplate.opsForZSet() // opsForZSet(): ZSET 에서 조회
                .reverseRangeWithScores(generateKey(dateStr), 0, -1) // 점수(score) 기준으로 내림차순 정렬된 결과를 (0~-1(끝까지)) score 과 함께 조회한다.
                .stream()
                // peek(): 스택에서 개체를 제거하지 않고 개체값만 반환. 여기서는, 값(articleId)과 점수(score).
                .peek(tuple -> log.info("[HotArticleListRepository.readAll] articleId={}, score={}", tuple.getValue(), tuple.getScore()))
                // (value, score) 튜플에서 value(=articleId)만 뽑아온다.
                .map(ZSetOperations.TypedTuple::getValue)
                .map(Long::valueOf)
                .toList();
    }

}
