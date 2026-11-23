package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The demand list is ordered by "campo" or "campo,asc|desc", only on the table's sortable fields. */
class DemandListSortTest {

    @Test
    void defaultsToNewestFirst() {
        assertThat(DemandService.listSort(null)).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        assertThat(DemandService.listSort(" ")).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
    }

    @Test
    void fieldAloneIsDescendingAsBefore() {
        assertThat(DemandService.listSort("title").getOrderFor("title").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void directionIsRead() {
        assertThat(DemandService.listSort("estimatedBudget,asc").getOrderFor("estimatedBudget").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(DemandService.listSort("desiredDate, DESC").getOrderFor("desiredDate").getDirection()).isEqualTo(Sort.Direction.DESC);
        /* The table's «Área responsável» column. */
        assertThat(DemandService.listSort("areaCode,asc").getOrderFor("areaCode").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void idBreaksTies() {
        assertThat(DemandService.listSort("code,asc").getOrderFor("id")).isNotNull();
    }

    @Test
    void unknownFieldIsRejected() {
        assertThatThrownBy(() -> DemandService.listSort("deletedAt"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("deletedAt");
    }
}
