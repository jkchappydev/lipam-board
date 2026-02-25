package lipam.board.common.outboxmessagerelay;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Outbox Pattern + Kafka 유실 방지 설정을 구현
 * 흐름
 * 1. DB 트랜잭션 커밋
 * 2. Outbox 테이블에 저장된 이벤트를 비동기로 Kafka 전송
 * 3. 실패하면 → 스케줄러가 미전송 이벤트를 재전송
 * 4. acks=all 로 브로커 복제 완료 후 성공 처리
 */
@EnableAsync // 트랜잭션이 끝나면 Kafka 이벤트 전송을 비동기로 처리하기 위해 사용
@Configuration
// 각 서비스 모듈은 @SpringBootApplication이 있는 메인 패키지 하위만 기본적으로 컴포넌트 스캔한다.
// 그래서 common 모듈의 lipam.board.common.outboxmessagerelay 패키지는 자동 스캔 대상에서 빠질 수 있어서,
// outbox-message-relay 관련 Bean 들을 사용하려고 명시적으로 스캔 범위를 추가한다.
@ComponentScan("lipam.board.common.outboxmessagerelay")
@EnableScheduling // 전송되지 않은(= outbox 에 남아있는) 이벤트들을 주기적으로 polling 해서 Kafka 로 보내기 위해 사용
public class MessageRelayConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // Kafka 로 이벤트를 전송할 Producer(KafkaTemplate)를 생성
    @Bean
    public KafkaTemplate<String, String> messageRelayKafkaTemplate() {
        // Kafka Producer 설정값 정의
        Map<String, Object> configProps = new HashMap<>();
        // Kafka Cluster 주소 설정
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        /**
         * hotArticles 서비스의 application.yaml 을 보면,
         *   consumer:
         *     group-id: lipam-board-hot-article-service
         *     key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
         *     value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
         * 이렇게 StringDeserializer를 사용하고 있으므로,
         * Producer 도 동일하게 StringSerializer 로 설정해서
         * Consumer 측에서 정상적으로 역직렬화가 가능하도록 맞춘다.
         */
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // 이벤트 유실을 방지하기 위해 ACK 를 all 로 설정
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");

        // 위에서 설정한 Producer 설정을 기반으로 KafkaTemplate 객체를 생성해서 반환
        // (이 Bean 이 실제 Kafka 로 이벤트를 전송하는 역할을 한다.)
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(configProps));
    }

    // 트랜잭션이 끝난 후 이벤트를 비동기로 전송하기 위한 스레드 풀
    @Bean
    public Executor messageRelayPublishEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);   // 기본 스레드 개수
        executor.setMaxPoolSize(50);    // 최대 스레드 개수
        executor.setQueueCapacity(100); // 작업 대기 큐 크기
        executor.setThreadNamePrefix("mr-pub-event-"); // 스레드 이름 prefix

        // 설정이 완료된 ThreadPoolTaskExecutor 를 Bean 으로 반환
        // (트랜잭션 종료 후 이벤트를 비동기로 전송할 때 사용된다.)
        return executor;
    }

    // 아직 Kafka 로 전송되지 않은 미전송 이벤트들을 일정 주기(예: 10초 이후)로 polling 해서 전송하기 위한 스레드 풀
    @Bean
    public Executor messageRelayPublishPendingEventExecutor() {
        // 각 애플리케이션 마다 샤드가 조금씩 분할되어 할당되기 때문에, 미전송 이벤트 처리는 싱글 스레드로 순차 처리
        // 단일 스레드 기반의 스케줄러 Executor 를 반환
        // (미전송 이벤트를 주기적으로 polling 해서 재전송할 때 사용된다.)
        return Executors.newSingleThreadScheduledExecutor();
    }

}