package lipam.board.like.repository;

import jakarta.persistence.LockModeType;
import lipam.board.like.entity.ArticleLikeCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleLikeCountRepository extends JpaRepository<ArticleLikeCount, Long> {

    // 비관적 락 - 방법 2
    // select ... for update 자동 처리 (@Lock)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ArticleLikeCount> findLockedByArticleId(Long articleId);

    @Query(
            value = "update article_like_count " +
                    "set like_count = like_count + 1 " +
                    "where article_id = :articleId",
            nativeQuery = true
    )
    @Modifying // update query 실행 시 필요함
    int increase(
            @Param("articleId") Long articleId
    );

    @Query(
            value = "update article_like_count " +
                    "set like_count = like_count - 1 " +
                    "where article_id = :articleId",
            nativeQuery = true
    )
    @Modifying
    int decrease(
            @Param("articleId") Long articleId
    );

}
