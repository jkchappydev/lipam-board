package lipam.board.view.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lipam.board.view.entity.ArticleViewCount;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class ArticleViewCountBackUpRepositoryTest {

    @Autowired
    ArticleViewCountBackUpRepository articleViewCountBackUpRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Test
    @Transactional
    void updateViewCountTest() {
        // given
        articleViewCountBackUpRepository.save(
                ArticleViewCount.init(1L, 0L)
        );

        entityManager.flush();
        entityManager.clear();

        // when
        // 백업 시점에 동시 요청으로 조회수가 100 → 300 → 200 순으로 들어와도, 현재 저장된 값보다 작은 값(200)으로는 갱신되지 않도록 방어한다.
        int result1 = articleViewCountBackUpRepository.updateViewCount(1L, 100L);
        int result2 = articleViewCountBackUpRepository.updateViewCount(1L, 300L);
        int result3 = articleViewCountBackUpRepository.updateViewCount(1L, 200L); // 현재 데이터(300L) 보다 더 작은수(200L) 로 업데이트 불가능

        // then
        Assertions.assertThat(result1).isEqualTo(1); // result1 업데이트 결과 O
        Assertions.assertThat(result2).isEqualTo(1); // result2 업데이트 결과 O
        Assertions.assertThat(result3).isEqualTo(0); // result3 업데이트 결과 X

        ArticleViewCount articleViewCount = articleViewCountBackUpRepository.findById(1L).get();
        Assertions.assertThat(articleViewCount.getViewCount()).isEqualTo(300L);
    }

}