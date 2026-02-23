package lipam.board.hotarticle.service;

import lipam.board.common.event.Event;
import lipam.board.common.event.EventPayload;
import lipam.board.hotarticle.repository.ArticleCreatedTimeRepository;
import lipam.board.hotarticle.repository.HotArticleListRepository;
import lipam.board.hotarticle.service.eventhandler.EventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class HotArticleScoreUpdater {

    private final HotArticleListRepository hotArticleListRepository;
    private final HotArticleScoreCalculator hotArticleScoreCalculator;
    private final ArticleCreatedTimeRepository articleCreatedTimeRepository;

    private static final long HOT_ARTICLE_COUNT = 10;
    private static final Duration HOT_ARTICLE_TTL = Duration.ofDays(10);

    public void update(Event<EventPayload> event, EventHandler<EventPayload> eventHandler) {
        Long articleId = eventHandler.findArticleId(event); // event 에 대한 payload 를 검사하고 이벤트핸들러에서 ArticleId 추출
        LocalDateTime createdTime = articleCreatedTimeRepository.read(articleId); // 찾은 ArticleId 로 생성시간을 찾음

        // 생성 시간이 오늘이 아니라면 이벤트를 처리하지 않는다.
        if (!isArticleCreatedToday(createdTime)) {
            return;
        }

        // 오늘 작성된 게시글이면 이벤트를 처리한다.
        eventHandler.handle(event);

        // 게시글에 대한 인기글 점수를 계산한다.
        long score = hotArticleScoreCalculator.calculate(articleId);
        hotArticleListRepository.add(
                articleId,
                createdTime,
                score,
                HOT_ARTICLE_COUNT,
                HOT_ARTICLE_TTL
        );

    }

    private boolean isArticleCreatedToday(LocalDateTime createdTime) {
        return createdTime != null && createdTime.toLocalDate().equals(LocalDate.now());
    }

}
