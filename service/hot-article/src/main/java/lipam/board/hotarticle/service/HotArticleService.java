package lipam.board.hotarticle.service;

import lipam.board.common.event.Event;
import lipam.board.common.event.EventPayload;
import lipam.board.common.event.EventType;
import lipam.board.hotarticle.client.ArticleClient;
import lipam.board.hotarticle.repository.HotArticleListRepository;
import lipam.board.hotarticle.service.eventhandler.EventHandler;
import lipam.board.hotarticle.service.response.HotArticleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

// HotArticleRepository 에는 게시글 ID 만 저장, 원본 게시글의 정보는 여기서 조회한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleService {

    private final ArticleClient articleClient;
    private final List<EventHandler> eventHandlers;
    private final HotArticleScoreUpdater hotArticleScoreUpdater;
    private final HotArticleListRepository hotArticleListRepository;

    // 인기글 이벤트를 통해 인기글 점수를 계산해서, HotArticleListRepository 에 ArticleId 를 저장하는 로직
    public void handleEvent(Event<EventPayload> event) {  // 인기글 서비스는 Consumer 이기 때문에, 이벤트를 Kafka 를 통해서 전달받음.
        // 1. 이벤트에 대응하는 이벤트 핸들러를 찾는다.
        EventHandler<EventPayload> eventHandler = findEventHandler(event);
        if (eventHandler == null) {
            return; // 이벤트에 처리될수 있는 로직들이 없다는걸 의미하기 때문에 단순 리턴 처리
        }

        if (isArticleCreatedOrDeleted(event)) {
            eventHandler.handle(event); // 생성 또는 삭제 이벤트이면 이벤트를 그대로 처리한다.
        } else {
            hotArticleScoreUpdater.update(event, eventHandler); // 생성 또는 삭제 이벤트가 아니라면 점수를 업데이트 해야한다.
        }
    }

    private EventHandler<EventPayload> findEventHandler(Event<EventPayload> event) {
        return eventHandlers.stream()
                .filter(eventHandler -> eventHandler.supports(event)) // 지원하는 EventHandler 타입인지 검사
                .findAny()
                .orElse(null);
    }

    // 이벤트 타입 검사 메서드(게시글 생성 또는 게시글 삭제 이벤트이어야만 한다.)
    private boolean isArticleCreatedOrDeleted(Event<EventPayload> event) {
        return EventType.ARTICLE_CREATED == event.getType() || EventType.ARTICLE_DELETED == event.getType();
    }

    public List<HotArticleResponse> readAll(String dateStr) { // yyyyMMdd
        return hotArticleListRepository.readAll(dateStr).stream()
                .map(articleClient::read) // 원본 데이터 반환
                .filter(Objects::nonNull) // 조회 실패(null)한 게시글은 제외
                .map(HotArticleResponse::from) // 조회한 원본 데이터를 HotArticleResponse 로 변환
                .toList();
    }

}
