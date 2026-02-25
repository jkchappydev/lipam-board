package lipam.board.common.outboxmessagerelay;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    // 특정 shardKey(=샤드) 에 해당하고,
    // createdAt이 from(기준시간)보다 오래된(outbox 에 오래 남아있는) 이벤트들을
    // createdAt 내림차순으로 조회한다. (pageable 로 조회 개수 제한)
    List<Outbox> findAllByShardKeyAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            Long shardKey,
            LocalDateTime from,
            Pageable pageable
    );

}
