package lipam.board.article;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

// 기본 엔티티 스캔은 @SpringBootApplication(= lipam.board.article) 하위만 보는데,
// Outbox 엔티티는 common 모듈(lipam.board.common.outboxmessagerelay)에 있어서 스캔에서 빠질 수 있다.
// 그래서 "lipam.board" 전체를 엔티티 스캔 대상으로 확장해서 Outbox 같은 공통 엔티티도 JPA 가 인식하게 한다.
@EntityScan(basePackages = "lipam.board")
// JPA Repository 스캔도 기본은 메인 패키지(lipam.board.article) 하위만 보는데,
// OutboxRepository는 lipam.board.common.outboxmessagerelay 에 있어서 빈 등록이 안 될 수 있다.
// 그래서 "lipam.board" 전체를 Repository 스캔 대상으로 확장해서 OutboxRepository 를 빈으로 등록되게 한다.
@EnableJpaRepositories(basePackages = "lipam.board")
@SpringBootApplication
public class ArticleApplication {

    public static void main(String[] args) {
       SpringApplication.run(ArticleApplication.class, args);
    }

}
