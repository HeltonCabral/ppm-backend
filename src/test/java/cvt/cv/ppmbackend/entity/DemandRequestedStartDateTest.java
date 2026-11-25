package cvt.cv.ppmbackend.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** A start date given for the demand (Ex.: the import sheet) is its planned start, whatever the complexity says. */
class DemandRequestedStartDateTest {

    @Test
    void requestedStartWinsOverDesiredDateMinusDuration() {
        Demand demand = new Demand();
        demand.setDesiredDate(LocalDate.of(2027, 12, 30));
        demand.setRequestedStartDate(LocalDate.of(2027, 3, 1));

        assertThat(demand.resolvePlannedStartDate(6)).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void withoutRequestedStartTheDurationIsUsed() {
        Demand demand = new Demand();
        demand.setDesiredDate(LocalDate.of(2027, 12, 30));

        assertThat(demand.resolvePlannedStartDate(6)).isEqualTo(LocalDate.of(2027, 6, 30));
        assertThat(demand.resolvePlannedStartDate(null)).isNull();
    }

    @Test
    void savingKeepsTheRequestedStartEvenIfSomethingClearedIt() {
        Demand demand = new Demand();
        demand.setRequestedStartDate(LocalDate.of(2027, 3, 1));
        demand.setPlannedStartDate(null);

        demand.keepRequestedStartDate();

        assertThat(demand.getPlannedStartDate()).isEqualTo(LocalDate.of(2027, 3, 1));
    }
}
