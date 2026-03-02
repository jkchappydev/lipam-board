package lipam.board.articleread.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

// Redis 에 게시글 아이디(boardId) 만 따로 ZSet 에 저장하기 위한 클래스
@Repository
@RequiredArgsConstructor
public class ArticleIdListRepository {

    private final StringRedisTemplate redisTemplate;

    // article-read::board::{boardId}::article-list
    private static final String KEY_FORMAT = "article-read::board::%s::article-list";

    // data = article_id, score = article_id(생성 시간)
    public void add(Long boardId, Long articleId, Long limit) {
        redisTemplate.executePipelined((RedisCallback<?>) action -> {
            StringRedisConnection conn = (StringRedisConnection) action;
            String key = generateKey(boardId);
            // 여기서 왜 toPaddedString(articleId) 했냐면
            // zAdd() 의 두번째 파라미터(score) 타입이 double 인데,
            // Long(articleId) 값이 double(score) 로 변환될 때, double 은 long 의 큰 수를 정확히 표현하지 못하기 때문에
            // 데이터가 유실되어 목록 데이터가 꼬이는 상황이 발생할 수도 있다.
            // 그래서 score 는 0으로 고정하고, articleId 는 value 에 19자리 고정폭 문자열로 저장한다.
            // score 가 모두 동일하면 value 기준(사전식 정렬)으로 순서가 결정되는데,
            // 이렇게 하면 score 가 같은 경우에는 value(toPaddedString(articleId)) 기준으로 정렬이 결정돼서,
            // 결과적으로 "최신 글(큰 articleId) 우선" 정렬을 안정적으로 유지할 수 있다.
            conn.zAdd(key, 0, toPaddedString(articleId));
            conn.zRemRange(key, 0, - limit - 1); // limit 개수만 유지하고 오래된 데이터 제거
            return null;
        });
    }

    // 특정 게시글(articleId)을 해당 게시판의 Redis 목록(ZSET)에서 제거
    public void delete(Long boardId, Long articleId) {
        redisTemplate.opsForZSet().remove(generateKey(boardId), toPaddedString(articleId));
    }

    // 페이지 번호 방식 목록 조회
    public List<Long> readAll(Long boardId, Long offset, Long limit) {
        return redisTemplate.opsForZSet()
                // ZSET 이 [6, 5, 4, 3, 2, 1] (최신 -> 과거) 로 정렬되어 있다고 가정하면
                // offset=0, limit=3 → index 0~2 조회 → [6,5,4]
                // offset=3, limit=3 → index 3~5 조회 → [3,2,1]
                // 즉, 내림차순 정렬된 상태에서 페이지(offset) 기준으로 일부 구간만 조회한다.
                .reverseRange( // score(생성 시간) 기준으로 정렬된 상태를 조회하려면 reverseRange() 를 사용해야 한다.
                        generateKey(boardId),
                        offset,
                        offset + limit - 1
                )
                .stream()
                .map(Long::valueOf)
                .toList();
    }

    // 무한 스크롤 방식 목록 조회
    public List<Long> readAllInfiniteScroll(Long boardId, Long lastArticleId, Long limit) {
        return redisTemplate.opsForZSet()
                // 문자열(value) 기준 정렬 상태로 조회하려면 reverseRangeByLex() 사용
                .reverseRangeByLex(
                        generateKey(boardId),
                        // Range = 조회할 구간(범위)
                        // Bound = 그 구간의 경계값(포함(inclusive)/제외(exclusive) 설정 가능)
                        // lastArticleId는 "마지막으로 받은 글" 기준점이다.
                        // 예) limit=3, 정렬이 6 5 4 3 2 1(최신 -> 과거) 일 때
                        //    1페이지로 [6,5,4]를 받았으면 lastArticleId=4 가 된다.
                        //
                        // 다음 페이지는 4보다 과거 데이터만 필요
                        // 4 자체는 제외(exclusive)하고 3,2,1만 대상으로 잡는다.
                        lastArticleId == null ?
                                Range.unbounded() : // 첫 요청이면 기준점이 없으니 전체 범위에서 최신부터 조회
                                Range.leftUnbounded(Range.Bound.exclusive(toPaddedString(lastArticleId))),
                        Limit.limit().count(limit.intValue())
                ).stream().map(Long::valueOf).toList();
    }

    // Long articleId 를 19자리 고정 길이 문자열로 변환
    // -> 문자열 정렬이 숫자 정렬과 동일하게 동작하도록 보장
    private String toPaddedString(Long articleId) {
        return "%019d".formatted(articleId); // 1234 -> 0000000000000001234
    }

    private String generateKey(Long boardId) {
        return KEY_FORMAT.formatted(boardId);
    }

}
