package lipam.board.common.outboxmessagerelay;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AssignedShardTest {

    @Test
    void ofTest() {
        // given
        Long shardCount = 64L; // 샤드가 임의로 64개 있다고 가정
        List<String> appList = List.of("appId1", "appId2", "appId3"); // Coordinator 에 의해 실행중인 애플리케이션 목록(정렬된 상태라고 가정)

        // when
        AssignedShard assignedShard1 = AssignedShard.of(appList.get(0), appList, shardCount);
        AssignedShard assignedShard2 = AssignedShard.of(appList.get(1), appList, shardCount);
        AssignedShard assignedShard3 = AssignedShard.of(appList.get(2), appList, shardCount);

        // 목록에 없는 appId를 넣으면 appIndex=-1 이므로 빈 샤드가 나와야 함
        AssignedShard assignedShard4 = AssignedShard.of("invalid", appList, shardCount);

        // then
        // appId1~3이 할당받은 샤드들을 전부 합치면(중복 없이) 0~63 전체가 나와야 한다.
        List<Long> result = Stream.of(assignedShard1.getShards(), assignedShard2.getShards(),
                        assignedShard3.getShards(), assignedShard4.getShards())
                // flatMap(List::stream) 예시
                // assignedShard1.getShards() = [0, 1]
                // assignedShard2.getShards() = [2, 3]
                // assignedShard3.getShards() = [4, 5]
                // assignedShard4.getShards() = []
                //
                // Stream.of(...) 결과 = Stream<List<Long>>  (리스트들의 스트림)
                // flatMap 후 결과   = Stream<Long>         (0,1,2,3,4,5 로 "펼쳐짐")
                // toList 후 결과    = List<Long>           [0,1,2,3,4,5]
                .flatMap(List::stream)
                .toList();

        // 전체 합친 샤드 개수는 shardCount(64)와 같아야 한다.
        assertThat(result).hasSize(shardCount.intValue());

        // 결과가 0 ~ 63 순서대로 전부 존재해야 한다.
        for (int i = 0; i < 64; i++) {
            assertThat(result.get(i)).isEqualTo(i);
        }

        // invalid appId는 할당받을 샤드가 없으므로 빈 리스트여야 한다.
        assertThat(assignedShard4.getShards()).isEmpty();
    }

}