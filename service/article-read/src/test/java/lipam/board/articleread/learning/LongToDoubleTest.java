package lipam.board.articleread.learning;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

public class LongToDoubleTest {

    @Test
    void longToDoubleTest() {
        long longValue = 111_111_111_111_111_111L; // 숫자 리터럴 '_' 구분자: 가독성용, 컴파일 시 제거(값에는 영향 없음)
        System.out.println("longValue = " + longValue);
        double doubleValue = longValue;
        System.out.println("doubleValue = " + new BigDecimal(doubleValue).toString());
        long longValue2 = (long) doubleValue;
        System.out.println("longValue2 = " + longValue2);

        // 출력 예시)
        // longValue  = 111111111111111111  (정확)
        // doubleValue= 111111111111111104  (double 정밀도 한계로 근처 값으로 반올림됨)
        // longValue2 = 111111111111111104  (반올림된 double 을 long 으로 변환한 결과)

        // 왜 104로 바뀌나?
        // double 은 큰 수에서 정수를 1씩 정확히 표현하지 못하고, 16 단위로 정확히 표현한다.
        // 111111111111111111은 16의 배수가 아니라서, 가장 가까운 16의 배수인 111111111111111104로 반올림된다.

        // 정리
        // - long  : 64비트 '정수' 타입이라 범위 내에서는 정수를 정확히 표현 가능
        // - double: 64비트 '부동소수점' 타입이라 큰 수 구간에서는 1 단위로 표현 불가
    }

}