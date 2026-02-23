package lipam.board.hotarticle.service;

import lipam.board.common.event.Event;
import lipam.board.common.event.EventType;
import lipam.board.hotarticle.service.eventhandler.EventHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotArticleServiceTest {

    @InjectMocks
    HotArticleService hotArticleService;

    @Mock
    List<EventHandler> eventHandlers; // 이벤트 타입별 처리기 목록(mock) - 어떤 핸들러가 선택되는지 제어하기 위해 사용

    @Mock
    HotArticleScoreUpdater hotArticleScoreUpdater;

    @Test
    void handleEventIfEventHandlerNotFoundTest() {
        // given
        Event event = mock(Event.class);
        EventHandler eventHandler = mock(EventHandler.class);
        given(eventHandler.supports(event)).willReturn(false); // 이 핸들러는 해당 이벤트를 지원하지 않는다고 가정
        given(eventHandlers.stream()).willReturn(Stream.of(eventHandler)); // 핸들러 목록에는 이 핸들러만 있다고 가정

        // when
        hotArticleService.handleEvent(event); // 이벤트 처리 로직 수행(지원하는 핸들러가 없으므로 바로 return 될 것)

        // then
        // 지원하는 핸들러를 찾지 못하면, handle/update 같은 후속 처리가 호출되면 안 된다.
        verify(eventHandler, never()).handle(event); // 이벤트 핸들러의 handle()이 호출되지 않아야 한다.
        verify(hotArticleScoreUpdater, never()).update(event, eventHandler); // 점수 업데이트도 호출되지 않아야 한다.
    }

    @Test
    void handleEventIfArticleCreatedEventTest() {
        // given
        Event event = mock(Event.class);
        given(event.getType()).willReturn(EventType.ARTICLE_CREATED); // 게시글 생성 이벤트라고 가정

        EventHandler eventHandler = mock(EventHandler.class);
        given(eventHandler.supports(event)).willReturn(true); // 이 핸들러가 해당 이벤트를 지원한다고 가정
        given(eventHandlers.stream()).willReturn(Stream.of(eventHandler));

        // when
        hotArticleService.handleEvent(event);

        // then
        // 생성 이벤트는 핸들러가 그대로 처리(handle)하고, 점수 업데이트 로직은 타지 않아야 한다.
        verify(eventHandler).handle(event); // 이벤트 핸들러의 handle()이 호출되어야 한다.
        verify(hotArticleScoreUpdater, never()).update(event, eventHandler); // 점수 업데이트는 호출되지 않아야 한다.
    }

    @Test
    void handleEventIfArticleDeletedEventTest() {
        // given
        Event event = mock(Event.class);
        given(event.getType()).willReturn(EventType.ARTICLE_DELETED); // 게시글 삭제 이벤트라고 가정

        EventHandler eventHandler = mock(EventHandler.class);
        given(eventHandler.supports(event)).willReturn(true);
        given(eventHandlers.stream()).willReturn(Stream.of(eventHandler));

        // when
        hotArticleService.handleEvent(event);

        // then
        // 삭제 이벤트는 핸들러가 그대로 처리(handle)하고, 점수 업데이트 로직은 타지 않아야 한다.
        verify(eventHandler).handle(event); // 이벤트 핸들러의 handle()이 호출되어야 한다.
        verify(hotArticleScoreUpdater, never()).update(event, eventHandler); // 점수 업데이트는 호출되지 않아야 한다.
    }

    @Test
    void handleEventIfScoreUpdatableEventTest() {
        // given
        Event event = mock(Event.class);
        given(event.getType()).willReturn(mock(EventType.class)); // 생성/삭제 이벤트만 아니면 되므로 임의의 이벤트 타입을 가정한다.

        EventHandler eventHandler = mock(EventHandler.class);
        given(eventHandler.supports(event)).willReturn(true);
        given(eventHandlers.stream()).willReturn(Stream.of(eventHandler));

        // when
        hotArticleService.handleEvent(event);

        // then
        // 생성/삭제 이벤트가 아니면, 핸들러가 직접 처리(handle)하지 않고 점수 업데이트 로직을 타야 한다.
        verify(eventHandler, never()).handle(event); // 이벤트 핸들러의 handle()은 호출되지 않아야 한다.
        verify(hotArticleScoreUpdater).update(event, eventHandler); // 점수 업데이트(update)는 호출되어야 한다.
    }

}