package lipam.board.articleread.cache;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Aspect
 *
 * Aspect는 AOP(Aspect Oriented Programming)를 구현하기 위한 클래스이다.
 *
 * 쉽게 말하면
 * "특정 메서드가 실행되기 전/후에 공통 로직을 끼워 넣을 수 있는 기능"이다.
 *
 * 이 클래스에서는
 * @OptimizedCacheable 이 붙은 메서드를 가로채서
 *
 * 1) 캐시 조회
 * 2) 캐시 miss 이면 실제 메서드 실행
 * 3) 결과를 캐시에 저장
 *
 * 이러한 캐시 처리 로직을 수행할 수 있다.
 */

/**
 * @Aspect
 *
 * 이 클래스가 AOP Aspect 역할을 하는 클래스임을 Spring에게 알려준다.
 * 즉, 메서드를 가로채는 기능을 가진 클래스라는 의미이다.
 */
@Aspect

/**
 * @Component
 *
 * Spring Bean으로 등록하기 위한 애노테이션이다.
 * Spring이 이 클래스를 자동으로 생성하여 관리할 수 있다.
 */
@Component

/**
 * @RequiredArgsConstructor (Lombok)
 *
 * final 필드에 대한 생성자를 자동으로 만들어준다.
 * 즉, OptimizedCacheManager 를 생성자를 통해 주입받을 수 있다.
 */
@RequiredArgsConstructor
public class OptimizedCacheAspect {

    private final OptimizedCacheManager optimizedCacheManager;

    /**
     * @Around
     *
     * 특정 메서드를 "감싸서" 실행할 수 있는 AOP 애노테이션이다.
     *
     * @annotation(OptimizedCacheable)
     * → @OptimizedCacheable 이 붙은 메서드가 실행될 때 이 메서드가 먼저 실행된다.
     *
     * 즉,
     * Controller / Service 의 메서드에 @OptimizedCacheable 이 붙어 있으면
     * 실제 메서드 실행 전에 이 around() 메서드가 먼저 호출된다.
     */
    @Around("@annotation(OptimizedCacheable)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {

        // 현재 실행되는 메서드의 @OptimizedCacheable 애노테이션을 가져온다.
        OptimizedCacheable cacheable = findAnnotation(joinPoint);

        // 실제 캐시 처리 로직은 OptimizedCacheManager 에 위임한다.
        return optimizedCacheManager.process(
                cacheable.type(),           // 캐시 타입 (캐시 key 구분용)
                cacheable.ttlSeconds(),     // 캐시 TTL
                joinPoint.getArgs(),        // 메서드 파라미터 (캐시 key 생성용)
                findReturnType(joinPoint),  // 반환 타입 (캐시 역직렬화용)
                () -> joinPoint.proceed()   // 캐시 miss 시 실제 메서드 실행
        );
    }

    /**
     * 현재 실행된 메서드에 붙어있는 @OptimizedCacheable 애노테이션을 찾는다.
     */
    private OptimizedCacheable findAnnotation(ProceedingJoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        return methodSignature.getMethod().getAnnotation(OptimizedCacheable.class);
    }

    /**
     * 현재 실행된 메서드의 반환 타입을 찾는다.
     *
     * 캐시에 저장된 데이터를 역직렬화할 때 사용된다.
     */
    private Class<?> findReturnType(ProceedingJoinPoint joinPoint) {
        Signature signature = joinPoint.getSignature();
        MethodSignature methodSignature = (MethodSignature) signature;
        return methodSignature.getReturnType();
    }

}