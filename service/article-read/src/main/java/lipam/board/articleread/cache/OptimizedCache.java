package lipam.board.articleread.cache;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lipam.board.common.dataserializer.DataSerializer;
import lombok.Getter;
import lombok.ToString;

import java.time.Duration;
import java.time.LocalDateTime;

// 캐시 최적화를 위한 Wrapper 클래스
@Getter
@ToString
public class OptimizedCache {

    private String data; // 실제 데이터
    private LocalDateTime expireAt; // 만료 시간

    public static OptimizedCache of(Object data, Duration ttl) { // 여기서의 ttl 은 logical ttl
        OptimizedCache optimizedCache = new OptimizedCache();
        optimizedCache.data = DataSerializer.serialize(data); // 실제 객체 데이터를 직렬화해서 문자열 형태로 저장한다.
        optimizedCache.expireAt = LocalDateTime.now().plus(ttl); // 현재 시간 + ttl 로 만료 시각을 계산한다.

        return optimizedCache;
    }

    // 캐시가 만료되었는지 판단
    @JsonIgnore
    // JSON 직렬화 시 isExpired() 가 boolean 필드처럼 포함될 수 있어서 제외한다.
    // (Getter 로 인식되어 JSON 필드로 들어가는 것을 방지하기 위함)
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expireAt); // 현재 시간이 만료 시간보다 이후면 만료
    }

    // 캐시에 저장된 문자열 데이터를 실제 객체 타입으로 변환한다.
    // 조회 시 data 를 역직렬화해서 원래 객체 형태로 사용할 수 있다.
    public <T> T parseData(Class<T> dataType) {
        return DataSerializer.deserialize(data, dataType);
    }

}
