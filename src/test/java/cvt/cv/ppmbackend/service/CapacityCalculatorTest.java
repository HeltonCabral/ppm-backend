package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.CapacityThresholdsProperties;
import cvt.cv.ppmbackend.dto.CapacityDtos.PeriodUtilization;
import cvt.cv.ppmbackend.dto.CapacityDtos.ProfileUtilization;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.enums.CapacityStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Valida o motor fase-aware que alimenta a capacidade de /planning-scenarios/plan/{id}/analysis
 * (via {@code CapacityCalculator.profileUtilizations}).
 */
class CapacityCalculatorTest {

    private CapacityCalculator calculator;
    private PhaseModel phaseModel;
    private PhaseDefinition dev;
    private PhaseDefinition test;

    @BeforeEach
    void setUp() {
        calculator = new CapacityCalculator(new CapacityThresholdsProperties());
        phaseModel = new PhaseModel();
        phaseModel.setId(UUID.randomUUID());
        dev = phase("DEV", "Desenvolvimento", 1, "50");
        test = phase("TEST", "Testes", 2, "50");
        phaseModel.getPhases().addAll(List.of(dev, test));
    }

    @Test
    void loadIsPlacedOnlyInTheMonthsOfTheRequirementPhases() {
        Profile analista = profile("Analista", 2, 10);
        Demand demand = demand(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        DemandProfileRequirement req = requirement(demand, analista, 2, dev);

        Map<UUID, ProfileUtilization> utilizations = calculator.profileUtilizations(List.of(req));
        ProfileUtilization util = utilizations.get(analista.getId());

        List<YearMonth> months = util.periods().stream().map(PeriodUtilization::period).toList();
        // A entrada só pede o perfil na 1.ª metade (DEV), por isso a 2.ª metade não tem carga.
        assertThat(months).contains(YearMonth.of(2027, 1));
        assertThat(months).doesNotContain(YearMonth.of(2027, 6));
        // Estado só pela ocupação simultânea: 1 demanda ÷ 10 = 10% → OK.
        assertThat(util.peakUtilization()).isEqualByComparingTo("10");
        assertThat(util.status()).isEqualTo(CapacityStatus.OK);
    }

    @Test
    void statusIsDrivenBySimultaneousDemandsNotByPeopleRatio() {
        // 1 demanda pede 11 pessoas de um perfil com apenas 2 disponíveis, mas 5 simultâneas.
        Profile admin = profile("Administrador de Sistemas", 2, 5);
        Demand demand = demand(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        DemandProfileRequirement req = requirement(demand, admin, 11);

        Map<UUID, ProfileUtilization> utilizations = calculator.profileUtilizations(List.of(req));
        ProfileUtilization util = utilizations.get(admin.getId());

        // 1 demanda ÷ 5 simultâneas = 20% → OK, apesar de 11 ÷ 2 pessoas.
        assertThat(util.peakUtilization()).isEqualByComparingTo("20");
        assertThat(util.status()).isEqualTo(CapacityStatus.OK);
    }

    @Test
    void statusBecomesCriticalWhenSimultaneousDemandsExceedCapacity() {
        Profile admin = profile("Administrador de Sistemas", 100, 2);
        Demand d1 = demand(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        Demand d2 = demand(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        Demand d3 = demand(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));

        Map<UUID, ProfileUtilization> utilizations = calculator.profileUtilizations(List.of(
                requirement(d1, admin, 1), requirement(d2, admin, 1), requirement(d3, admin, 1)));
        ProfileUtilization util = utilizations.get(admin.getId());

        // 3 demandas ÷ 2 simultâneas = 150% → CRÍTICA.
        assertThat(util.peakUtilization()).isEqualByComparingTo("150");
        assertThat(util.status()).isEqualTo(CapacityStatus.CRITICAL);
    }

    @Test
    void withoutPhasesTheLoadSpreadsOverTheWholeDemandWindow() {
        Profile analista = profile("Analista", 2, 10);
        Demand demand = demand(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        DemandProfileRequirement req = requirement(demand, analista, 2);

        Map<UUID, ProfileUtilization> utilizations = calculator.profileUtilizations(List.of(req));
        ProfileUtilization util = utilizations.get(analista.getId());

        List<YearMonth> months = util.periods().stream().map(PeriodUtilization::period).toList();
        assertThat(months).contains(YearMonth.of(2027, 1), YearMonth.of(2027, 6));
    }

    @Test
    void twoEntriesSameProfileDistributeLoadAcrossTheirOwnPhases() {
        Profile analista = profile("Analista", 2, 10);
        Demand demand = demand(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        DemandProfileRequirement devReq = requirement(demand, analista, 2, dev);
        DemandProfileRequirement testReq = requirement(demand, analista, 2, test);

        Map<UUID, ProfileUtilization> utilizations =
                calculator.profileUtilizations(List.of(devReq, testReq));
        ProfileUtilization util = utilizations.get(analista.getId());

        BigDecimal januaryLoad = loadAt(util, YearMonth.of(2027, 1));
        BigDecimal juneLoad = loadAt(util, YearMonth.of(2027, 6));
        // Janeiro é servido pela entrada DEV e Junho pela entrada TEST; ambos com carga 2.
        assertThat(januaryLoad).isEqualByComparingTo("2");
        assertThat(juneLoad).isEqualByComparingTo("2");
        // Uma única Demanda ocupa cada mês, mesmo com duas entradas do mesmo perfil.
        assertThat(simultaneousAt(util, YearMonth.of(2027, 1))).isEqualTo(1);
    }

    private BigDecimal loadAt(ProfileUtilization util, YearMonth period) {
        return util.periods().stream()
                .filter(p -> p.period().equals(period))
                .map(PeriodUtilization::requiredLoad)
                .findFirst()
                .orElseThrow();
    }

    private int simultaneousAt(ProfileUtilization util, YearMonth period) {
        return util.periods().stream()
                .filter(p -> p.period().equals(period))
                .map(PeriodUtilization::simultaneousDemands)
                .findFirst()
                .orElseThrow();
    }

    private PhaseDefinition phase(String code, String name, int sequence, String weight) {
        PhaseDefinition phase = new PhaseDefinition();
        phase.setId(UUID.randomUUID());
        phase.setPhaseModel(phaseModel);
        phase.setCode(code);
        phase.setName(name);
        phase.setSequence(sequence);
        phase.setDurationWeightPercentage(new BigDecimal(weight));
        phase.setActive(true);
        return phase;
    }

    private Profile profile(String name, int available, int simultaneous) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setName(name);
        profile.setAvailableCapacity(available);
        profile.setSimultaneousDemandCapacity(simultaneous);
        profile.setActive(true);
        return profile;
    }

    private Demand demand(LocalDate start, LocalDate end) {
        Demand demand = new Demand();
        demand.setId(UUID.randomUUID());
        demand.setPlannedStartDate(start);
        demand.setDesiredDate(end);
        demand.setType(phaseModel);
        return demand;
    }

    private DemandProfileRequirement requirement(Demand demand, Profile profile, int quantity,
            PhaseDefinition... phases) {
        DemandProfileRequirement req = new DemandProfileRequirement();
        req.setId(UUID.randomUUID());
        req.setDemand(demand);
        req.setProfile(profile);
        req.setRequiredQuantity(quantity);
        Set<PhaseDefinition> set = new LinkedHashSet<>(List.of(phases));
        req.setPhases(set);
        return req;
    }
}
