package lipam.board.articleread.client;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Optional;

// Command 서버로 원본 데이터를 요청하기 위한 Client 클래스들 (ArticleClient, CommentClient, LikeClient, ViewClient)
// Redis(Read Model)에 데이터가 없으면(Command 쪽 DB는 Read 서비스가 직접 못 보니)
// Command 서버 API 를 호출해서 원본 데이터를 가져올 수 있다.
@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleClient {

    private RestClient restClient;

    @Value("${endpoints.lipam-board-article-service.url}")
    private String articleServiceUrl;

    @PostConstruct
    public void initRestClient() {
        restClient = RestClient.create(articleServiceUrl);
    }

    public Optional<ArticleResponse> read(Long articleId) {
        try {
            // Command 서버(쓰기 모델)에서 게시글 원본 데이터를 조회한다.
            ArticleResponse articleResponse = restClient.get()
                    .uri("/v1/articles/{articleId}", articleId)
                    .retrieve()
                    .body(ArticleResponse.class);

            // 404/에러/역직렬화 실패 같은 경우를 대비해서 Optional 로 감싼다.
            return Optional.ofNullable(articleResponse);
        } catch (Exception e) {
            log.error("[ArticleClient.read] articleId={}]", articleId, e);
            return Optional.empty();
        }
    }

    // Command 서버 응답(JSON)을 그대로 매핑 받기 위한 DTO
    @Getter
    public static class ArticleResponse {
        private Long articleId;
        private String title;
        private String content;
        private Long boardId;
        private Long writerId;
        private LocalDateTime createdAt;
        private LocalDateTime modifiedAt;
    }

}
