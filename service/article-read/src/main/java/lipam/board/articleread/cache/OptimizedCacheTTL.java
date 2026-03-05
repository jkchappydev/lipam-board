package lipam.board.articleread.cache;

import lombok.Getter;

import java.time.Duration;

// ttl 을 받아서 logical ttl 과 physical ttl 계산
@Getter
public class OptimizedCacheTTL {

    private Duration logicalTTL;
    private Duration physicalTTL;

    public static final long PHYSICAL_TTL_DELAY_SECONDS = 5;

    public static OptimizedCacheTTL of(long ttlSeconds) {
        OptimizedCacheTTL optimizedCacheTTL = new OptimizedCacheTTL();
        optimizedCacheTTL.logicalTTL = Duration.ofSeconds(ttlSeconds);
        optimizedCacheTTL.physicalTTL = optimizedCacheTTL.logicalTTL.plusSeconds(PHYSICAL_TTL_DELAY_SECONDS); // physicalTTL 은 logicalTTL 보다 길다. 그래야 logicalTTL 이 만료되어서 캐시 갱신을 요청하더라도, physicalTTL 로 인해 데이터는 남아있어서 원본 데이터로 요청을 하지 않는다.
        return optimizedCacheTTL;
    }

}
