package lipam.board.articleread.service;

import lipam.board.articleread.client.ArticleClient;
import lipam.board.articleread.client.CommentClient;
import lipam.board.articleread.client.LikeClient;
import lipam.board.articleread.client.ViewClient;
import lipam.board.articleread.repository.ArticleQueryModel;
import lipam.board.articleread.repository.ArticleQueryModelRepository;
import lipam.board.articleread.service.event.handler.EventHandler;
import lipam.board.articleread.service.response.ArticleReadResponse;
import lipam.board.common.event.Event;
import lipam.board.common.event.EventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Command 서비스에서 이벤트 발생
 *         ↓
 * Outbox → Kafka 이벤트 발행
 *         ↓
 * article-read 서비스 Consumer 수신
 *         ↓
 * EventHandler (Created / Updated / Deleted / Like / Comment 등)
 *         ↓
 * Redis ArticleQueryModel(Read Model) 생성 및 갱신
 *         ↓
 * ArticleReadService 가 Redis 기반 조회 처리
 */
// Kafka 이벤트 기반으로 Read Model을 동기화하고, Redis 조회 모델을 통해 게시글 조회를 처리하는 서비스
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleReadService {

    // Redis(Read Model)에 데이터가 없을 때 Command 서버로 원본 데이터를 조회하기 위한 클라이언트들
    private final ArticleClient articleClient;
    private final CommentClient commentClient;
    private final LikeClient likeClient;
    private final ViewClient viewClient;

    // Redis 에 저장되는 조회 전용 모델(ArticleQueryModel) 저장소
    private final ArticleQueryModelRepository articleQueryModelRepository;

    // Kafka Consumer 가 이벤트를 받으면, 이벤트 타입별로 처리하는 핸들러 목록
    private final List<EventHandler> eventHandlers;

    public void handleEvent(Event<EventPayload> event) {
        // 들어온 이벤트를 처리할 수 있는 핸들러를 찾아서 실행
        for (EventHandler eventHandler : eventHandlers) {
            // 이 핸들러가 해당 이벤트 타입을 지원하면 처리(handle)
            if (eventHandler.supports(event)) {
                eventHandler.handle(event);
            }
        }
    }

    // 게시글 데이터를 조회하기 위한 메서드
    public ArticleReadResponse read(Long articleId) {
        ArticleQueryModel articleQueryModel = articleQueryModelRepository.read(articleId)
                .or(() -> fetch(articleId)) // articleQueryModelRepository(Redis) 에서 데이터를 꺼내는데, 없으면 fetch() 로 command 서버에서 데이터 직접 호출
                .orElseThrow();// Command 서버에서도 못 가져오면 조회 실패 처리

        return ArticleReadResponse.from(
                articleQueryModel,
                viewClient.count(articleId)
        );
    }

    private Optional<ArticleQueryModel> fetch(Long articleId) {
        // Command 서버에서 원본 게시글을 조회하고, 댓글수/좋아요수까지 붙여서 QueryModel 생성
        Optional<ArticleQueryModel> articleQueryModelOptional = articleClient.read(articleId)
                .map(article -> ArticleQueryModel.create( // command 서버에서 가져온 데이터가 있다면, ArticleQueryModel 생성
                        article,
                        commentClient.count(articleId),
                        likeClient.count(articleId)
                ));

        // Command 서버에서 데이터 조회가 성공했으면, Redis(Read Model)에 TTL 1일로 캐시 저장
        articleQueryModelOptional
                .ifPresent(articleQueryModel -> articleQueryModelRepository.create(articleQueryModel, Duration.ofDays(1)));
        log.info("[ArticleReadService.fetch] fetch data. articleId={}, isPresent={}", articleId, articleQueryModelOptional.isPresent());

        return articleQueryModelOptional;
    }

}
