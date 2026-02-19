package lipam.board.view.service;

import lipam.board.view.repository.ArticleViewCountRepository;
import lipam.board.view.repository.ArticleViewDistributedLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ArticleViewService {

    private final ArticleViewCountRepository articleViewCountRepository;
    private final ArticleViewCountBackUpProcessor articleViewCountBackUpProcessor;
    private final ArticleViewDistributedLockRepository articleViewDistributedLockRepository;

    private static final int BACK_UP_BATCH_SIZE = 100; // 개수 단위로 백업 (조회수 100개 단위)
    private static final Duration TTL = Duration.ofMinutes(10); // TTL 10분

    public Long increase(Long articleId, Long userId) {
        // 분산 락 획득 시도
        // 획득에 실패하면 현재 조회수 반환
        if (!articleViewDistributedLockRepository.lock(articleId, userId, TTL)) {
            return articleViewCountRepository.read(articleId);
        }

        Long count = articleViewCountRepository.increase(articleId);
        if (count % BACK_UP_BATCH_SIZE == 0) {
            articleViewCountBackUpProcessor.backUp(articleId, count);
        }

        return count;
    }

    public Long count(Long articleId) {
        return articleViewCountRepository.read(articleId);
    }

}
