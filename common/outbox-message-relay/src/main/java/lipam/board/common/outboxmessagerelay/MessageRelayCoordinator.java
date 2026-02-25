package lipam.board.common.outboxmessagerelay;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

// 살아있는 애플리케이션들을 추적하고 관리 (Redis 의 ZSET 이용)
@Component
@RequiredArgsConstructor
public class MessageRelayCoordinator {

    private final StringRedisTemplate redisTemplate;

    @Value("${spring.application.name}")
    private String applicationName;

    private final String APP_ID = UUID.randomUUID().toString(); // 현재 실행된 애플리케이션 인스턴스를 구분하기 위한 고유 ID

    private final int PING_INTERVAL_SECONDS = 3; // ping 주기 (3초마다)
    private final int PING_FAILURE_THRESHOLD = 3; // ping이 끊긴 것으로 판단할 기준(3번 누락)

    // 현재 인스턴스(APP_ID)가 담당할 샤드 목록을 계산해서 반환
    public AssignedShard assignedShard() {
        return AssignedShard.of(APP_ID, findAppIds(), MessageRelayConstants.SHARD_COUNT);
    }

    // Redis 에 등록된 "현재 실행 중인 애플리케이션(appId) 목록"을 가져온다.
    // appId로 정렬해두면 실행 중인 애플리케이션들의 순서가 고정돼서, 샤드 분배 기준이 안정적이다.
    private List<String> findAppIds() {
        return redisTemplate.opsForZSet().reverseRange(generateKey(), 0, -1) // ZSET 전체 조회 (score 기준 내림차순)
                .stream()
                .sorted() // appId로 정렬 (샤드 분배 기준을 고정하기 위해)
                .toList();
    }

    // 3초마다 ping 을 보내서 "현재 실행 중인 애플리케이션"임을 Redis 에 갱신한다.
    // 동시에 일정 시간 이상 ping 이 없는 애플리케이션은 살아있는 목록에서 제거한다.
    @Scheduled(fixedDelay = PING_INTERVAL_SECONDS, timeUnit = TimeUnit.SECONDS)
    public void ping() {
        // 한 번의 통신으로 여러개의 연산을 한번에 전송
        redisTemplate.executePipelined((RedisCallback<?>) action -> {
            StringRedisConnection conn = (StringRedisConnection) action;
            String key = generateKey();
            conn.zAdd(key, Instant.now().toEpochMilli(), APP_ID);
            conn.zRemRangeByScore(
                    key,
                    Double.NEGATIVE_INFINITY,
                    Instant.now().minusSeconds(PING_INTERVAL_SECONDS * PING_FAILURE_THRESHOLD).toEpochMilli() // 9초 이상 ping 이 갱신되지 않은 appId를 Redis ZSET 에서 제거
            );

            return null;
        });
    }

    @PreDestroy // 빈(애플리케이션)이 종료되기 직전에 호출되어 정리 작업을 수행한다.
    public void leave() {
        redisTemplate.opsForZSet().remove(generateKey(), APP_ID); // 종료 시 Redis 에 남아있는 자신의 appId를 정리해서, 불필요한 샤드 재할당이 발생하지 않도록 한다.
    }

    // 각 마이크로서비스별로 독립적인 애플리케이션이름을 가지고 있는데, 
    // MessageRelayCoordinator 모듈을 각 마이크로서비스에 독립적으로 붙혔을 때
    // 이 Coordinator 가 독립적인 키로 동작하도록 하기 위해 applicationName 을 키 파라미터로 함
    private String generateKey() {
        return "message-relay-coordinator::app-list::%s".formatted(applicationName);
    }

}
