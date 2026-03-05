package lipam.board.articleread.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 조회 성능 최적화를 위해 Redis 캐시를 적용할 메서드에 사용하는 커스텀 어노테이션.
 *
 * 해당 어노테이션이 붙은 메서드는 AOP에서 가로채 캐시 조회를 먼저 수행한다.
 * - 캐시에 데이터가 있으면 메서드를 실행하지 않고 캐시 데이터를 바로 반환 가능하다.
 * - 캐시에 데이터가 없으면 실제 메서드를 실행한 뒤 결과를 캐시에 저장 가능하다.
 *
 * type
 *  : 캐시 키 구분을 위한 타입 값 (도메인별 캐시 구분이 가능하다)
 *
 * ttlSeconds
 *  : 캐시 데이터의 TTL(Time To Live) 설정값 (초 단위로 만료시간 설정 가능하다)
 *
 * 처리 흐름
 * OptimizedCacheable (Annotation)
 *        ↓
 * OptimizedCacheAspect (AOP)
 *        ↓
 * RedisCacheRepository (Redis 조회/저장)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OptimizedCacheable {

    String type(); // 캐시 키 생성 시 사용할 캐시 타입 구분 값
    long ttlSeconds(); // 캐시 데이터 TTL (초 단위)

}