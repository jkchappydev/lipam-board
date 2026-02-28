package lipam.board.articleread.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration // Kafka 관련 설정 클래스
public class KafkaConfig {

    @Bean // KafkaListener 가 사용할 Listener Container Factory 빈 등록
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory
    ) {

        // KafkaListener 동작을 관리하는 컨테이너 팩토리 생성
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        // 실제 Kafka Consumer 생성에 사용할 ConsumerFactory 설정
        factory.setConsumerFactory(consumerFactory);

        // 수동 커밋 모드 설정
        // enable-auto-commit=false 환경이므로,
        // 메시지 처리 완료 후 ack.acknowledge()를 직접 호출해야 오프셋 커밋됨
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL);

        return factory;
    }

}