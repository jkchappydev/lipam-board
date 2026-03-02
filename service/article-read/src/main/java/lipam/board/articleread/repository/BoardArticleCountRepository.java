package lipam.board.articleread.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

// 페이지 번호 방식에서, 전체 게시글 개수도 같이 반환 해야하는데,
// 매번 articleService 에 count 요청을 하면 비용이 크니까,
// articleReadService 가 Redis 에 게시글 개수를 따로 저장해두고 읽어오도록 만든 저장소 클래스
@Repository
@RequiredArgsConstructor
public class BoardArticleCountRepository {

    private final StringRedisTemplate redisTemplate;

    // article-read::board-article-count::board::{boardId}
    private static final String KEY_FORMAT = "article-read::board-article-count::board::%s";

    // 게시글 개수 저장(없으면 생성, 있으면 갱신)
    public void createOrUpdate(Long boardId, Long articleCount) {
        redisTemplate.opsForValue().set(generateKey(boardId), String.valueOf(articleCount));
    }

    // 게시글 개수 조회 (없으면 0 반환)
    public Long read(Long boardId) {
        String result = redisTemplate.opsForValue().get(generateKey(boardId));
        return result == null ? 0L : Long.valueOf(result);
    }

    private String generateKey(Long boardId) {
        return KEY_FORMAT.formatted(boardId);
    }

}
