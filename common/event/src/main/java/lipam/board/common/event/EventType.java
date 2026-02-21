package lipam.board.common.event;

import lipam.board.common.event.payload.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@RequiredArgsConstructor
public enum EventType {

    ARTICLE_CREATED(ArticleCreatedEventPayload.class, Topic.LIPAM_BOARD_ARTICLE),
    ARTICLE_UPDATED(ArticleUpdatedEventPayload.class, Topic.LIPAM_BOARD_ARTICLE),
    ARTICLE_DELETED(ArticleDeletedEventPayload.class, Topic.LIPAM_BOARD_ARTICLE),
    COMMENT_CREATED(CommentCreatedEventPayload.class, Topic.LIPAM_BOARD_COMMENT),
    COMMENT_DELETED(CommentDeletedEventPayload.class, Topic.LIPAM_BOARD_COMMENT),
    ARTICLE_LIKED(ArticleLikedEventPayload.class, Topic.LIPAM_BOARD_LIKE),
    ARTICLE_UNLIKED(ArticleUnlikedEventPayload.class, Topic.LIPAM_BOARD_LIKE),
    ARTICLE_VIEWED(ArticleViewedEventPayload.class, Topic.LIPAM_BOARD_VIEW)
    ;

    private final Class<? extends EventPayload> payloadClass;
    private final String topic;

    public static EventType from(String type) {
        try {
            return valueOf(type);
        } catch (Exception e) {
            log.error("[EventType.from] type={}", type, e);
            return null;
        }
    }

    public static class Topic {
        public static final String LIPAM_BOARD_ARTICLE = "lipam-board-article";
        public static final String LIPAM_BOARD_COMMENT = "lipam-board-comment";
        public static final String LIPAM_BOARD_LIKE = "lipam-board-like";
        public static final String LIPAM_BOARD_VIEW = "lipam-board-view";
    }

}
