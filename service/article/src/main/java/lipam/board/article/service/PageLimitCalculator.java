package lipam.board.article.service;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// 유틸리티성 클래스
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PageLimitCalculator {

    public static Long calculatePageLimit(Long page, Long pageSize, Long movablePageCount) {
        return ((page - 1) / movablePageCount + 1) * pageSize * movablePageCount + 1;
    }

}
