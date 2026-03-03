package lipam.board.articleread.service;

import lipam.board.articleread.client.ArticleClient;
import lipam.board.articleread.client.CommentClient;
import lipam.board.articleread.client.LikeClient;
import lipam.board.articleread.client.ViewClient;
import lipam.board.articleread.repository.ArticleIdListRepository;
import lipam.board.articleread.repository.ArticleQueryModel;
import lipam.board.articleread.repository.ArticleQueryModelRepository;
import lipam.board.articleread.repository.BoardArticleCountRepository;
import lipam.board.articleread.service.event.handler.EventHandler;
import lipam.board.articleread.service.response.ArticleReadPageResponse;
import lipam.board.articleread.service.response.ArticleReadResponse;
import lipam.board.common.event.Event;
import lipam.board.common.event.EventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private ArticleIdListRepository articleIdListRepository;
    private BoardArticleCountRepository boardArticleCountRepository;

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
                viewClient.count(articleId) // 트래픽이 많으면 조회수 서비스에 모든 부하 전파 (캐시 이용, ViewClient 참고)
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

    // == 페이지 번호 방식 ==
    public ArticleReadPageResponse readAll(Long boardId, Long page, Long pageSize) {
        return ArticleReadPageResponse.of(
                readAll(
                        readAllArticleIds(boardId, page, pageSize)
                ),
                count(boardId)
        );
    }

    private List<ArticleReadResponse> readAll(List<Long> articleIds) {
        Map<Long, ArticleQueryModel> articleQueryModelMap = articleQueryModelRepository.readAll(articleIds);
        return articleIds.stream()
                .map(articleId -> articleQueryModelMap.containsKey(articleId) ?
                        articleQueryModelMap.get(articleId) :
                        fetch(articleId).orElse(null))
                .filter(Objects::nonNull)
                .map(articleQueryModel ->
                        ArticleReadResponse.from(
                                articleQueryModel,
                                viewClient.count(articleQueryModel.getArticleId())
                        ))
                .toList();
    }

    private List<Long> readAllArticleIds(Long boardId, Long page, Long pageSize) {
        List<Long> articleIds = articleIdListRepository.readAll(boardId, (page - 1) * pageSize, pageSize);
        if (pageSize == articleIds.size()) { // pageSize 와 articleIds (목록) 개수가 동일하다는 뜻은, 현재 page 에 대한 게시글 목록이 Redis 에 전부 저장되어 있는 것을 의미한다.
            log.info("[ArticleReadService.readAllArticleIds] return redis data.");
            return articleIds;
        }

        // Redis 에 데이터가 없는 경우, 원본 데이터를 가져온다.
        log.info("[ArticleReadService.readAllArticleIds] return origin data.");
        return articleClient.readAll(boardId, page, pageSize).getArticles().stream()
                .map(ArticleClient.ArticleResponse::getArticleId)
                .toList();
    }

    private long count(Long boardId) {
        Long result = boardArticleCountRepository.read(boardId);
        if (result != null) {
            return result;
        }

        // Redis 에 데이터가 없는 상황
        long count = articleClient.count(boardId);
        boardArticleCountRepository.createOrUpdate(boardId, count); // 적재
        return count;
    }
    // == 페이지 번호 방식 끝 ==

    // == 무한 스크롤 방식 ==
    public List<ArticleReadResponse> readAllInfiniteScroll(Long boardId, Long lastArticleId, Long pageSize) {
        return readAll(
                readAllInfiniteScrollArticleIds(boardId, lastArticleId, pageSize)
        );
    }

    private List<Long> readAllInfiniteScrollArticleIds(Long boardId, Long lastArticleId, Long pageSize) {
        List<Long> articleIds = articleIdListRepository.readAllInfiniteScroll(boardId, lastArticleId, pageSize);
        if (pageSize == articleIds.size()) { // pageSize 와 articleIds (목록) 개수가 동일하다는 뜻은, 현재 page 에 대한 게시글 목록이 Redis 에 전부 저장되어 있는 것을 의미한다.
            log.info("[ArticleReadService.readAllInfiniteScrollArticleIds] return redis data.");
            return articleIds;
        }
        // Redis 에 데이터가 없는 경우, 원본 데이터를 가져온다.
        log.info("[ArticleReadService.readAllInfiniteScrollArticleIds] return origin data.");
        return articleClient.readAllInfiniteScroll(boardId, lastArticleId, pageSize).stream()
                .map(ArticleClient.ArticleResponse::getArticleId)
                .toList();
    }
    // == 무한 스크롤 방식 끝 ==

}
