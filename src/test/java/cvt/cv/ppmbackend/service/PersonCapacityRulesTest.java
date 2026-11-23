package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.PersonCapacity;
import cvt.cv.ppmbackend.enums.CapacityReason;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersonCapacityRulesTest {

    @Test
    void withoutRecordsThePersonIsAtFullCapacity() {
        assertThat(PersonCapacityRules.onDay(LocalDate.of(2027, 3, 1), List.of()).percentage()).isEqualTo(100);
        assertThat(PersonCapacityRules.inMonth(YearMonth.of(2027, 3), List.of()).reason()).isNull();
    }

    @Test
    void datedRecordsOverrideThePermanentOneEvenWhenHigher() {
        List<PersonCapacity> records = List.of(
                record(60, CapacityReason.OPERATIONS, null, null),
                record(80, CapacityReason.TRAINING, "2027-03-01", "2027-03-05"));

        assertThat(PersonCapacityRules.onDay(LocalDate.of(2027, 3, 3), records))
                .isEqualTo(new PersonCapacityRules.Effective(80, CapacityReason.TRAINING));
        assertThat(PersonCapacityRules.onDay(LocalDate.of(2027, 3, 6), records).percentage()).isEqualTo(60);
    }

    @Test
    void overlappingDatedRecordsKeepTheLowest() {
        List<PersonCapacity> records = List.of(
                record(50, CapacityReason.TRAINING, "2027-03-01", "2027-03-31"),
                record(20, CapacityReason.OTHER, "2027-03-10", "2027-03-20"));

        assertThat(PersonCapacityRules.onDay(LocalDate.of(2027, 3, 15), records).percentage()).isEqualTo(20);
        assertThat(PersonCapacityRules.inMonth(YearMonth.of(2027, 3), records))
                .isEqualTo(new PersonCapacityRules.Effective(20, CapacityReason.OTHER));
    }

    @Test
    void openEndedRecordsCountFromTheirStart() {
        List<PersonCapacity> records = List.of(record(40, CapacityReason.PART_TIME, "2027-04-15", null));

        assertThat(PersonCapacityRules.inMonth(YearMonth.of(2027, 3), records).percentage()).isEqualTo(100);
        assertThat(PersonCapacityRules.inMonth(YearMonth.of(2027, 4), records).percentage()).isEqualTo(40);
    }

    private static PersonCapacity record(int percentage, CapacityReason reason, String from, String until) {
        PersonCapacity capacity = new PersonCapacity();
        capacity.setCapacityPercentage(percentage);
        capacity.setReason(reason);
        capacity.setValidFrom(from == null ? null : LocalDate.parse(from));
        capacity.setValidUntil(until == null ? null : LocalDate.parse(until));
        return capacity;
    }
}
