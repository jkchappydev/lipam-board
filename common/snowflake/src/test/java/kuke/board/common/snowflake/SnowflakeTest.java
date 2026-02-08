package kuke.board.common.snowflake;

import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class SnowflakeTest {
	Snowflake snowflake = new Snowflake();

	@Test
	void nextIdTest() throws ExecutionException, InterruptedException {
		// given
		ExecutorService executorService = Executors.newFixedThreadPool(10); // 10 개의 스레드 풀 생성
		List<Future<List<Long>>> futures = new ArrayList<>();
		int repeatCount = 1000; // 1000 번 동안
		int idCount = 1000; // 1000 개의 아이디 생성 (총 백만개)

		// when
		for (int i = 0; i < repeatCount; i++) {
			futures.add(executorService.submit(() -> generateIdList(snowflake, idCount)));
		}

		// then
		List<Long> result = new ArrayList<>();
		for (Future<List<Long>> future : futures) {
			List<Long> idList = future.get();
			for (int i = 1; i < idList.size(); i++) {
				assertThat(idList.get(i)).isGreaterThan(idList.get(i - 1)); // 오름차순으로 생성 되었는가
			}
			result.addAll(idList);
		}
		assertThat(result.stream().distinct().count()).isEqualTo(repeatCount * idCount); // 중복없이 갯수를 세었을 때 백만개인지 = 통과하면 중복없이 생성됨

		executorService.shutdown();
	}

	List<Long> generateIdList(Snowflake snowflake, int count) {
		List<Long> idList = new ArrayList<>();
		while (count-- > 0) {
			idList.add(snowflake.nextId());
		}
		return idList;
	}

	// 위에랑 똑같은 코드인데 시간 측정하는 기능 추가되어있음.
	@Test
	void nextIdPerformanceTest() throws InterruptedException {
		// given
		ExecutorService executorService = Executors.newFixedThreadPool(10);
		int repeatCount = 1000;
		int idCount = 1000;
		CountDownLatch latch = new CountDownLatch(repeatCount);

		// when
		long start = System.nanoTime();
		for (int i = 0; i < repeatCount; i++) {
			executorService.submit(() -> {
				generateIdList(snowflake, idCount);
				latch.countDown();
			});
		}

		latch.await();

		long end = System.nanoTime();
		System.out.println("times = %s ms".formatted((end - start) / 1_000_000));

		executorService.shutdown();
	}
}