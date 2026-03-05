package lipam.board.articleread.cache;

// 함수형 인터페이스
// 추상 메서드가 하나만 있는 인터페이스로, 람다(lambda)나 메서드 레퍼런스로 구현을 전달할 수 있다.
// 캐시 miss 발생 시 원본 데이터를 가져오는 로직을 "함수 형태로 전달"하기 위해 사용한다.
//
// 예)
// cache.get(key, () -> articleClient.read(articleId));
//
// 위처럼 원본 데이터 조회 로직을 람다로 넘길 수 있다.
@FunctionalInterface
public interface OptimizedCacheOriginDataSupplier<T> {

    // 원본 데이터를 조회하는 메서드
    // 캐시에 데이터가 없을 때 이 메서드를 실행해서 실제 데이터를 가져온다.
    // Throwable 을 선언해서 원본 조회 로직에서 발생하는 예외를 그대로 전달할 수 있다.
    T get() throws Throwable;

}
