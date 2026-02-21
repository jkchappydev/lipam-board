package lipam.board.common.dataserializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE) // 유틸 클래스: AccessLevel.PRIVATE 해서 기본 생성자(public)가 자동 생성되는 걸 막아서 new 불가능하게 함
public final class DataSerializer { // 유틸 클래스: 상속/확장 의도 없으니 final 붙혀서 extends 방지

    private static final ObjectMapper objectMapper = initialize();

    // ObjectMapper 초기화
    private static ObjectMapper initialize() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule()) // LocalDate/LocalDateTime 같은 java.time 날짜/시간 타입 직렬화/역직렬화 지원
                // 역직렬화(JSON -> 객체) 할 때 없는 필드가 있으면 에러가 날 수 있는데, false 로 하면 에러가 나타나지 않음
                // 예시)
                // { "name": "kim", "age": 20, "extra": "??? " }
                // class User { String name; int age; } // extra 없음. 이 때, FAIL_ON_UNKNOWN_PROPERTIES = false 이면 extra 는 무시하고 name/age 만 채움
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // String 타입의 data 를 받아서, Class<T> 타입으로 역직렬화 하는 메서드
    public static <T> T deserialize(String data, Class<T> clazz) {
        try {
            return objectMapper.readValue(data, clazz);
        } catch (JsonProcessingException e) {
            log.error("[DataSerializer.deserialize] data={}, clazz={}", data, clazz, e);
            return null;
        }
    }

    // Object 타입의 data(Map/객체 등)를 받아서, Class<T> 타입으로 역직렬화 하는 메서드
    public static <T> T deserialize(Object data, Class<T> clazz) {
        return objectMapper.convertValue(data, clazz);
    }

    // Object 타입의 object 를 받아서, String(JSON) 타입으로 직렬화 하는 메서드
    public static String serialize(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("[DataSerializer.serialize] object={}", object, e);
            return null;
        }
    }

}
