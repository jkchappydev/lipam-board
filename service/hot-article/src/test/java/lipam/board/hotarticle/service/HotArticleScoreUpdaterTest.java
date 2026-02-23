package lipam.board.hotarticle.service;

import lipam.board.common.event.Event;
import lipam.board.hotarticle.repository.ArticleCreatedTimeRepository;
import lipam.board.hotarticle.repository.HotArticleListRepository;
import lipam.board.hotarticle.service.eventhandler.EventHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotArticleScoreUpdaterTest {

    @InjectMocks
    private HotArticleScoreUpdater hotArticleScoreUpdater;

    @Mock
    HotArticleListRepository hotArticleListRepository;

    @Mock
    HotArticleScoreCalculator hotArticleScoreCalculator;

    @Mock
    ArticleCreatedTimeRepository articleCreatedTimeRepository;

    @Test
    void updateIfArticleNotCreatedTodayTest() {
        // given
        Long articleId = 1L;
        Event event = mock(Event.class);  // 이벤트 객체를 mock으로 생성(실제 구현/데이터 불필요)
        EventHandler eventHandler = mock(EventHandler.class); // 이벤트 처리기 mock(행동 검증용)

        given(eventHandler.findArticleId(event)).willReturn(articleId); // 이벤트에서 articleId를 추출하면 1L이 나오도록 설정

        LocalDateTime createdTime = LocalDateTime.now().minusDays(1); // 어제 생성된 글로 가정
        given(articleCreatedTimeRepository.read(articleId)).willReturn(createdTime); // createdAt 조회 시 "어제" 시간 반환하도록 설정

        // when
        hotArticleScoreUpdater.update(event, eventHandler); // 업데이트 로직 수행

        // then
        // 오늘 생성된 글이 아니면 아무 처리도 하지 않아야 함.
        verify(eventHandler, never()).handle(event); // 이벤트 핸들러의 실제 처리(handle)가 호출되지 않아야 한다.
        verify(hotArticleListRepository, never()) // 인기글 목록(ZSET)에도 반영(add)되지 않아야 한다.
                .add(anyLong(), any(LocalDateTime.class), anyLong(), anyLong(), any(Duration.class));
    }

    @Test
    void updateTest() {
        // given
        Long articleId = 1L;
        Event event = mock(Event.class);
        EventHandler eventHandler = mock(EventHandler.class);

        given(eventHandler.findArticleId(event)).willReturn(articleId);

        LocalDateTime createdTime = LocalDateTime.now(); // 오늘 생성된 글로 가정
        given(articleCreatedTimeRepository.read(articleId)).willReturn(createdTime); // createdAt 조회 시 "오늘" 시간 반환하도록 설정

        // when
        hotArticleScoreUpdater.update(event, eventHandler);

        // then
        // 오늘 생성된 글이면, 이벤트 처리와 인기글 반영이 수행되어야 한다.
        verify(eventHandler).handle(event); // 이벤트 핸들러의 실제 처리(handle)가 호출되어야 한다.
        verify(hotArticleListRepository) // 인기글 목록(ZSET)에 반영(add)되어야 한다.
                .add(anyLong(), any(LocalDateTime.class), anyLong(), anyLong(), any(Duration.class));
    }

}