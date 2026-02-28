package lipam.board.articleread.service.event.handler;

import lipam.board.common.event.Event;
import lipam.board.common.event.EventPayload;

public interface EventHandler<T extends EventPayload> {

    void handle(Event<T> payload); // Event 를 처리하는 handle 메서드

    boolean supports(Event<T> event); // 이 인터페이스 구현체가 이 이벤트를 지원하는지 확인하기 위한 supports 메서드

}
