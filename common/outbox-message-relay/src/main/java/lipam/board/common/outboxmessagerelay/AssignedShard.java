package lipam.board.common.outboxmessagerelay;

import lombok.Getter;

import java.util.List;
import java.util.stream.LongStream;

// Shard 를 각 애플리케이션에 균등하게 할당하기 위한 클래스
// MessageRelayCoordinator 를 통해 만들어진다.
@Getter
public class AssignedShard {

    private List<Long> shards; // 현재 애플리케이션이 담당하게 될 샤드 목록

    /**
     * @param appId      지금 실행된 애플리케이션 아이디
     * @param appIds     Coordinator 에 의해 수집된 전체 실행 애플리케이션 목록 (정렬된 상태)
     * @param shardCount 전체 샤드 개수 (예: 4개)
     */
    public static AssignedShard of(String appId, List<String> appIds, long shardCount) {
        AssignedShard assignedShard = new AssignedShard();
        assignedShard.shards = assign(appId, appIds, shardCount); // 현재 애플리케이션에 할당된 샤드 계산

        return assignedShard;
    }

    // 전체 샤드를 애플리케이션 수에 맞게 균등 분배하여, 현재 애플리케이션이 담당할 샤드 목록을 계산하는 메서드
    private static List<Long> assign(String appId, List<String> appIds, long shardCount) {
        // 현재 애플리케이션이 전체 목록에서 몇 번째 인덱스인지 찾는다.
        int appIndex = findAppIndex(appId, appIds);

        if (appIndex == -1) {
            // 현재 애플리케이션이 목록에 없으면
            return List.of(); // 할당할 샤드가 없으므로 빈 리스트 반환
        }

        // 균등 분배를 위한 범위 계산
        /*
         * 균등 분배를 위한 범위 계산
         * shardCount = 4 고정
         * 예시 1)
         * appIds.size() = 2 인 경우
         * appIndex = 0 → start = 0, end = 1 → 0,1번 샤드 담당
         * appIndex = 1 → start = 2, end = 3 → 2,3번 샤드 담당
         * 예시 2)
         * appIds.size() = 4 인 경우
         * appIndex = 0 → 0번
         * appIndex = 1 → 1번
         * appIndex = 2 → 2번
         * appIndex = 3 → 3번
         */
        long start = appIndex * shardCount / appIds.size();
        long end = (appIndex + 1) * shardCount / appIds.size() - 1;

        // 계산된 범위의 샤드 번호를 리스트로 생성해서 반환
        return LongStream.rangeClosed(start, end).boxed().toList();
    }

    /**
     * appIds 는 현재 실행 중인 애플리케이션 목록을 정렬된 상태로 가지고 있다.
     * 해당 목록에서 appId 가 몇 번째 인덱스인지 찾아 반환한다.
     * 이 인덱스를 기준으로 샤드 분배 범위를 계산한다.
     */
    private static int findAppIndex(String appId, List<String> appIds) {
        for (int i = 0; i < appIds.size(); i++) {
            if (appIds.get(i).equals(appId)) {
                return i; // 현재 애플리케이션의 위치 반환
            }
        }

        return -1; // 목록에 없으면 -1 반환
    }

}
