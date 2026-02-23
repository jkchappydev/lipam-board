package lipam.board.hotarticle.service;

import lipam.board.hotarticle.repository.ArticleCommentCountRepository;
import lipam.board.hotarticle.repository.ArticleLikeCountRepository;
import lipam.board.hotarticle.repository.ArticleViewCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HotArticleScoreCalculator {

    private final ArticleLikeCountRepository articleLikeCountRepository; // 좋아요수
    private final ArticleCommentCountRepository articleCommentCountRepository; // 댓글수
    private final ArticleViewCountRepository articleViewCountRepository; // 조회수

    // 가중치
    private static final long ARTICLE_LIKE_COUNT_WEIGHT = 3;
    private static final long ARTICLE_COMMENT_COUNT_WEIGHT = 2;
    private static final long ARTICLE_VIEW_COUNT_WEIGHT = 1;

    // 인기글 점수를 계산하는 메서드
    // 점수 = 좋아요수*3 + 댓글수*2 + 조회수*1
    public long calculate(Long articleId) {
        Long articleLikeCount = articleLikeCountRepository.read(articleId);
        Long articleCommentCount = articleCommentCountRepository.read(articleId);
        Long articleViewCount = articleViewCountRepository.read(articleId);

        return articleLikeCount * ARTICLE_LIKE_COUNT_WEIGHT
                + articleCommentCount * ARTICLE_COMMENT_COUNT_WEIGHT
                + articleViewCount * ARTICLE_VIEW_COUNT_WEIGHT;
    }

}
