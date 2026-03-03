package lipam.board.articleread.service.event.handler;

import lipam.board.articleread.repository.ArticleIdListRepository;
import lipam.board.articleread.repository.ArticleQueryModel;
import lipam.board.articleread.repository.ArticleQueryModelRepository;
import lipam.board.articleread.repository.BoardArticleCountRepository;
import lipam.board.common.event.Event;
import lipam.board.common.event.EventType;
import lipam.board.common.event.payload.ArticleCreatedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

// 게시글 생성 이벤트를 기반으로 Read Model에 조회용 게시글 데이터를 생성하는 핸들러
@Component
@RequiredArgsConstructor
public class ArticleCreatedEventHandler implements EventHandler<ArticleCreatedEventPayload> {

    private final ArticleQueryModelRepository articleQueryModelRepository;
    private final ArticleIdListRepository articleIdListRepository;
    private final BoardArticleCountRepository boardArticleCountRepository;

    @Override
    public void handle(Event<ArticleCreatedEventPayload> event) {
        // ARTICLE_CREATED 이벤트 발생 시,
        // Redis(Read Model)에 ArticleQueryModel 을 생성하여 조회 모델을 동기화한다.
        ArticleCreatedEventPayload payload = event.getPayload();
        // 순서 중요
        articleQueryModelRepository.create(
                ArticleQueryModel.create(payload),
                Duration.ofDays(1)
        );
        articleIdListRepository.add(payload.getBoardId(), payload.getArticleId(), 1000L); // 게시판별 게시글 ID 목록(정렬/페이징용)을 Redis(ZSET)에 추가. Redis 에는 1000개만 저장
        boardArticleCountRepository.createOrUpdate(payload.getBoardId(), payload.getBoardArticleCount()); // 게시판별 전체 게시글 수 캐시를 Redis 에 저장/갱신한다. (목록 조회 시 count API 호출을 줄이기 위한 용도)

        // 이 순서라면, 목록에는 생성되었지만 게시글 조회는 안되는 상황이 발생할 수도 있음
        /*
        articleIdListRepository.add(payload.getBoardId(), payload.getArticleId(), 1000L); // Redis 에는 1000개만 저장
        articleQueryModelRepository.create(
                ArticleQueryModel.create(payload),
                Duration.ofDays(1)
        );
        boardArticleCountRepository.createOrUpdate(payload.getBoardId(), payload.getBoardArticleCount());
        */
    }

    @Override
    public boolean supports(Event<ArticleCreatedEventPayload> event) {
        return EventType.ARTICLE_CREATED == event.getType();
    }

}
