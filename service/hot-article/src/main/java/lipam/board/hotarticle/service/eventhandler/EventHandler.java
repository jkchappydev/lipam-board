package lipam.board.hotarticle.service.eventhandler;

import lipam.board.common.event.Event;
import lipam.board.common.event.EventPayload;

public interface EventHandler<T extends EventPayload> {

    void handle(Event<T> event); // 이벤트를 받았을 때 처리되는 로직을 정의하는 메서드

    boolean supports(Event<T> event); // 이벤트 핸들러 구현체가 해당 이벤트를 지원하는지 확인하기 위한 메서드

    Long findArticleId(Event<T> event); // 이벤트가 어떤 Article 에 대한건지 ArticleId 를 찾아주는 메서드

}
