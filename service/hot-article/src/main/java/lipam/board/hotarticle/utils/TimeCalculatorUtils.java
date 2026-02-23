package lipam.board.hotarticle.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TimeCalculatorUtils {

    // 지금부터 다음 자정(00:00)까지 남은 시간을 Duration 으로 반환한다.
    // Redis TTL 을 자정에 맞추기 위해 사용하는 메서드이다.
    // now      = 2026-02-23T11:15:30
    // midnight = 2026-02-24T00:00:00
    // Duration = midnight - now = 12시간 44분 30초
    public static Duration calculateDurationToMidnight() {
        LocalDateTime now = LocalDateTime.now(); // 현재 시각 구하고,
        LocalDateTime midnight = now.plusDays(1).with(LocalTime.MIDNIGHT); // 현재 날짜에 하루를 더한 뒤, 시간을 00:00으로 맞춰 다음 자정 시각을 만든다.
        return Duration.between(now, midnight); // 현재 시각부터 다음 자정까지 남은 시간을 계산해 반환한다.
    }

}
