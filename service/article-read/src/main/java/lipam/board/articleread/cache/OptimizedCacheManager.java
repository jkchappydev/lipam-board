package lipam.board.articleread.cache;

import lipam.board.common.dataserializer.DataSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

import static java.util.stream.Collectors.joining;

// OptimizedCacheManager 를 통해 캐시를 조회하고,
// 캐시가 없거나(미적재) / 논리적으로 만료된 경우 원본 데이터를 조회해서 갱신한다.
@Component
@RequiredArgsConstructor
public class OptimizedCacheManager {

    private final StringRedisTemplate redisTemplate;
    private final OptimizedCacheLockProvider optimizedCacheLockProvider;

    // 캐시 키를 만들 때 파라미터가 여러 개 붙을 수 있어서 구분자를 둔다.
    // 예) prefix=a, args=[1,2] -> a::1::2
    private static final String DELIMITER = "::";

    // 캐시에서 데이터를 가져오고, 필요하면 원본 데이터를 호출해서 갱신하는 메서드
    // - type        : 캐시 타입(prefix). 어떤 데이터 캐시인지 구분하기 위한 값
    // - ttlSeconds  : TTL(초). OptimizedCacheTTL 에서 logical/physical TTL로 변환된다.
    // - args        : 캐시 키에 포함될 파라미터 목록
    // - returnType  : 캐시에 들어있는 data 를 어떤 타입으로 역직렬화할지 결정한다.
    // - originDataSupplier : 캐시 미스/만료 시 원본 데이터를 가져오는 로직
    public Object process(String type, long ttlSeconds, Object[] args, Class<?> returnType, OptimizedCacheOriginDataSupplier<?> originDataSupplier) throws Throwable {
        // type + args 로 캐시 키를 만든다. (예: "article::1::20")
        String key = generateKey(type, args);

        // Redis 에서 캐시 데이터를 문자열로 가져온다.
        String cachedData = redisTemplate.opsForValue().get(key);

        // 캐시가 없는 경우(미적재) -> 바로 원본 데이터 조회 후 캐시에 적재한다.
        if (cachedData == null) {
            return refresh(originDataSupplier, key, ttlSeconds);
        }

        // 캐시가 있는 경우 -> OptimizedCache 형태로 역직렬화한다.
        OptimizedCache optimizedCache = DataSerializer.deserialize(cachedData, OptimizedCache.class);
        if (optimizedCache == null) { // 역직렬화가 실패했으면(깨진 데이터/형식 불일치 등) 안전하게 원본 조회로 갱신한다.
            return refresh(originDataSupplier, key, ttlSeconds);
        }

        // 논리 TTL 기준으로 아직 만료가 아니면 캐시 데이터를 그대로 반환한다.
        if (!optimizedCache.isExpired()) {
            return optimizedCache.parseData(returnType); // 데이터를 그대로 반환한다.
        }

        // 논리적으로 만료된 경우: 여기서부터는 분산 락으로 1건만 갱신하도록 제한한다.
        // 락 획득 실패 = 누군가 이미 갱신 중 -> 오래된 캐시라도 일단 데이터 반환
        if (!optimizedCacheLockProvider.lock(key)) {
            return optimizedCache.parseData(returnType);
        }

        // 락 획득 성공 - 갱신 담당
        try {
            return refresh(originDataSupplier, key, ttlSeconds);
        } finally {
            optimizedCacheLockProvider.unlock(key); // 갱신이 성공하든 실패하든 락은 해제한다.
        }

    }

    // 원본 데이터를 조회하고, logical/physical TTL 정책에 맞게 Redis 에 다시 적재한다.
    private Object refresh(OptimizedCacheOriginDataSupplier<?> originDataSupplier, String key, long ttlSeconds) throws Throwable {
        // 원본 데이터를 조회한다.
        Object result = originDataSupplier.get();

        // ttlSeconds 를 기준으로 logicalTTL + physicalTTL 을 계산한다.
        // - logicalTTL : expireAt(논리 만료) 계산에 사용
        // - physicalTTL: Redis 실제 TTL(물리 만료)로 사용
        OptimizedCacheTTL optimizedCacheTTL = OptimizedCacheTTL.of(ttlSeconds);

        // 원본 데이터를 직렬화해서 OptimizedCache(data + expireAt) 형태로 만든다.
        OptimizedCache optimizedCache = OptimizedCache.of(result, optimizedCacheTTL.getLogicalTTL());

        // Redis 에 적재한다.
        // - value: OptimizedCache 직렬화 문자열
        // - ttl  : physical TTL (Redis 에서 실제로 삭제되는 시간)
        redisTemplate.opsForValue()
                .set(
                        key,
                        DataSerializer.serialize(optimizedCache),
                        optimizedCacheTTL.getPhysicalTTL()
                );

        // 호출한 쪽에는 원본 결과를 그대로 반환한다.
        return result;
    }

    private String generateKey(String prefix, Object[] args) {
        // prefix = a, args = [1, 2]
        // a::1::2
        // prefix 는 cache 가 어떤 타입인지 구분하기 위한 타입
        return prefix + DELIMITER +
                Arrays.stream(args)
                        .map(String::valueOf)
                        .collect(joining(DELIMITER));
    }

}
