package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.PersonCapacity;
import cvt.cv.ppmbackend.enums.CapacityReason;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * Capacidade efetiva de uma pessoa. Num dia: os registos com datas que o cobrem sobrepõem-se ao permanente e,
 * entre os que sobram, conta o mais baixo; sem registo, 100. Num mês: o valor mais baixo de qualquer dia,
 * para que uma semana de ausência não se esconda na média.
 */
public final class PersonCapacityRules {
    public static final int FULL = 100;

    private PersonCapacityRules() {
    }

    /** {@code reason} nulo quando a pessoa está a 100%. */
    public record Effective(int percentage, CapacityReason reason) {
        public static final Effective FULL_TIME = new Effective(FULL, null);

        public String reasonLabel() {
            return reason == null ? null : reason.label();
        }
    }

    public static Effective onDay(LocalDate day, List<PersonCapacity> records) {
        if (records == null || records.isEmpty()) {
            return Effective.FULL_TIME;
        }
        List<PersonCapacity> dated = records.stream().filter(r -> !r.isPermanent() && r.covers(day)).toList();
        List<PersonCapacity> pool = !dated.isEmpty() ? dated
                : records.stream().filter(PersonCapacity::isPermanent).toList();
        return pool.stream()
                .min(Comparator.comparing(PersonCapacity::getCapacityPercentage))
                .map(r -> new Effective(r.getCapacityPercentage(), r.getReason()))
                .orElse(Effective.FULL_TIME);
    }

    public static Effective inMonth(YearMonth month, List<PersonCapacity> records) {
        if (records == null || records.isEmpty()) {
            return Effective.FULL_TIME;
        }
        Effective lowest = Effective.FULL_TIME;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            Effective effective = onDay(month.atDay(day), records);
            if (effective.percentage() < lowest.percentage()) {
                lowest = effective;
            }
        }
        return lowest;
    }

    public static int maxConcurrentOnDay(LocalDate day, List<PersonCapacity> records, int defaultValue) {
        if (records == null || records.isEmpty()) return defaultValue;
        List<PersonCapacity> dated = records.stream().filter(r -> !r.isPermanent() && r.covers(day)).toList();
        List<PersonCapacity> pool = !dated.isEmpty() ? dated
                : records.stream().filter(PersonCapacity::isPermanent).toList();
        return pool.stream().map(PersonCapacity::getMaxConcurrent).filter(java.util.Objects::nonNull)
                .min(Integer::compareTo).orElse(defaultValue);
    }

    public static int maxConcurrentInMonth(YearMonth month, List<PersonCapacity> records, int defaultValue) {
        int limit = defaultValue;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            limit = Math.min(limit, maxConcurrentOnDay(month.atDay(day), records, defaultValue));
        }
        return limit;
    }
}
