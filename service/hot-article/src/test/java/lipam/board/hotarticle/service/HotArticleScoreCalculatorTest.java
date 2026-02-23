package lipam.board.hotarticle.service;

import lipam.board.hotarticle.repository.ArticleCommentCountRepository;
import lipam.board.hotarticle.repository.ArticleLikeCountRepository;
import lipam.board.hotarticle.repository.ArticleViewCountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

// 단위 테스트
@ExtendWith(MockitoExtension.class)
class HotArticleScoreCalculatorTest {

    @InjectMocks // 테스트 대상 객체(HotArticleScoreCalculator)를 생성하고, @Mock 으로 만든 의존성을 자동 주입한다.
    HotArticleScoreCalculator hotArticleScoreCalculator;

    // @Mock : 의존성을 가짜 객체(mock)로 만든다.
    @Mock
    ArticleLikeCountRepository articleLikeCountRepository;

    @Mock
    ArticleCommentCountRepository articleCommentCountRepository;

    @Mock
    ArticleViewCountRepository articleViewCountRepository;

    @Test
    void calculateTest() {
        // given
        Long articleId = 1L;
        long likeCount = RandomGenerator.getDefault().nextLong(100); // 좋아요 수를 임의로 생성(0~99)
        long commentCount = RandomGenerator.getDefault().nextLong(100); // 댓글 수를 임의로 생성(0~99)
        long viewCount = RandomGenerator.getDefault().nextLong(100); // 조회수를 임의로 생성(0~99)

        given(articleLikeCountRepository.read(articleId)).willReturn(likeCount);  // like 저장소가 articleId를 조회하면 likeCount 를 반환
        given(articleCommentCountRepository.read(articleId)).willReturn(commentCount); // comment 저장소가 articleId를 조회하면 commentCount를 반환
        given(articleViewCountRepository.read(articleId)).willReturn(viewCount); // view 저장소가 articleId를 조회하면 viewCount를 반환

        // when
        long score = hotArticleScoreCalculator.calculate(articleId);

        // 계산된 점수는 (좋아요*3 + 댓글*2 + 조회수*1) 공식과 같아야 한다.
        assertThat(score)
                .isEqualTo(3 * likeCount + 2 * commentCount + 1 * viewCount);
    }

}