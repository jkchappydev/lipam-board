package lipam.board.common.event;

import lipam.board.common.dataserializer.DataSerializer;
import lombok.Getter;

@Getter
public class Event<T extends EventPayload> {

    private Long eventId; // 이벤트에 대한 고유한 아이디
    private EventType type; // 발생한 이벤트가 어떤 타입인지 확인
    private T payload; // 이벤트가 어떤 데이터를 가지고 있는지 확인

    public static Event<EventPayload> of(Long eventId, EventType type, EventPayload payload) {
        Event<EventPayload> event = new Event<>();
        event.eventId = eventId;
        event.type = type;
        event.payload = payload;

        return event;
    }

    // Event 객체를 json 문자열로 변경
    public String toJson() {
        return DataSerializer.serialize(this); // this (현재 객체) 전달
    }

    public static Event<EventPayload> fromJson(String json) {
        EventRow eventRow = DataSerializer.deserialize(json, EventRow.class);
        if (eventRow == null) {
            return null;
        }

        Event<EventPayload> event = new Event<>();
        event.eventId = eventRow.getEventId();
        event.type = EventType.from(eventRow.getType());
        event.payload = DataSerializer.deserialize(eventRow.getPayload(), event.type.getPayloadClass());

        return event;
    }

    @Getter
    private static class EventRow {
        private Long eventId;
        private String type;
        private Object payload;
    }

}
