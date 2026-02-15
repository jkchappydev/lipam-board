package lipam.board.comment.data;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lipam.board.comment.entity.Comment;
import lipam.board.comment.entity.CommentPath;
import lipam.board.comment.entity.CommentV2;
import lipam.board.common.snowflake.Snowflake;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class DataInitializerV2Test {

    @PersistenceContext
    EntityManager entityManager;

    @Autowired
    TransactionTemplate transactionTemplate;

    Snowflake snowflake = new Snowflake();
    CountDownLatch latch = new CountDownLatch(EXECUTE_COUNT); // 모든 작업이 끝날 때까지 메인 스레드를 대기시키고, 각 작업 완료 시 countDown()으로 감소하여 0이 되면 await()가 해제되는 동기화 장치이다.

    static final int BULK_INSERT_SIZE = 2000; // 한 번의 insert() 호출(= 한 트랜잭션)에서 몇 건을 넣을지(배치 크기) 정의한다.
    static final int EXECUTE_COUNT = 6000; // insert() 작업을 총 몇 번 실행할지(= 스레드 풀에 제출할 작업 개수) 정의한다.

    // show-sql: false 로 할 것
    // 멀티스레드로 트랜잭션 단위 bulk insert 를 반복 실행해 Article 더미 데이터를 대량 생성. (1200 만 건)
    @Test
    void initialize() throws InterruptedException {
        // 멀티 스레드로 작업할 스레드 풀 10개로 설정.
        ExecutorService executorService = Executors.newFixedThreadPool(10);

        // 총 6000 번의 insert() 멀티 스레드로 실행한다.
        for (int i = 0; i < EXECUTE_COUNT; i++) {
            // insert() 는 멀티 스레드에서 실행중인데, path 는 유니크 인덱스이므로 중복없이 생성되어야 함.
            // 모든 멀티 스레드에서 중복없는 숫자를 생성하기 위해 start 와 end 로 범위를 지정하고, insert(start, end) 파라미터로 넘긴다.
            // insert(start, end) 는 지정한 start, end 범위에 대해서 for 반복문의 i 값에 넣고 실행하면, 해당 반복문은 중복없이 숫자가 생성된다.
            int start = i * BULK_INSERT_SIZE;
            int end = (i + 1) * BULK_INSERT_SIZE;
            executorService.submit(() -> {
                insert(start, end); // 실제 DB 로 insert 한다.
                latch.countDown(); // CountDownLatch 의 count 를 1 감소시킨다.
                System.out.println("latch.getCount() = " + latch.getCount());
            });
        }

        // latch 카운트가 0이 될 때까지(=모든 작업이 끝날 때까지) 메인 스레드가 종료되지 않도록 대기시킨다.
        // 모든 작업이 끝나기 전에 initialize()가 먼저 끝나면, 스프링 컨텍스트/DB 커넥션이 정리돼서 insert 가 중간에 끊길 수 있다.
        // 그래서 latch 가 0 될 때까지 메인 스레드를 잡아두고, 6000번 작업이 다 끝난 다음에 initialize() 가 종료되게 한다.
        latch.await();

        // 그리고 더 이상 작업이 없으므로 스레드 풀을 종료한다.
        executorService.shutdown();
    }

    void insert(int start, int end) {
        transactionTemplate.executeWithoutResult(status -> {
            // 한번의 insert() (= 한 트랜잭션)에서 BULK_INSERT_SIZE(2000)건을 넣을 수 있다.
            for (int i = start; i < end; i++) {
                CommentV2 comment = CommentV2.create(
                        snowflake.nextId(),
                        "content",
                        1L,
                        1L,
                        toPath(i)
                );

                entityManager.persist(comment);
            }
        });
    }

    private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int DEPTH_CHUNK_SIZE = 5;

    private CommentPath toPath(int value) {
        String path = "";
        for (int i = 0; i < DEPTH_CHUNK_SIZE; i++) {
            path = CHARSET.charAt(value % CHARSET.length()) + path;
            value /= CHARSET.length();
        }

        return CommentPath.create(path);
    }

}
