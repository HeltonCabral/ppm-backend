package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import cvt.cv.ppmbackend.dto.DemandPlanningCycleDtos.ScoreHistoryEntry;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.entity.DemandPlanningCycleScoreHistory;
import cvt.cv.ppmbackend.entity.DemandScoring;
import cvt.cv.ppmbackend.entity.ScoringCriterion;
import cvt.cv.ppmbackend.entity.ScoringDimension;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.repository.DemandPlanningCycleScoreHistoryRepository;
import cvt.cv.ppmbackend.repository.DemandScoringRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandScoreLifecycleServiceTest {

    @Mock private DemandScoringRepository scoring;
    @Mock private DemandPlanningCycleScoreHistoryRepository scoreHistoryRepo;

    private DemandScoreLifecycleService service;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final List<DemandPlanningCycleScoreHistory> savedHistory = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new DemandScoreLifecycleService(mapper, scoring, scoreHistoryRepo);
        lenient().when(scoreHistoryRepo.save(any(DemandPlanningCycleScoreHistory.class))).thenAnswer(inv -> {
            DemandPlanningCycleScoreHistory h = inv.getArgument(0);
            if (h.getId() == null) h.setId(UUID.randomUUID());
            savedHistory.add(h);
            return h;
        });
    }

    @Test
    void invalidatingValidScoreCapturesSnapshotWithDimensionsAndPersistsHistory() throws Exception {
        DemandPlanningCycle cycle = validScoredCycle(new BigDecimal("51.50"), 3);
        stubItems(cycle.getId(), List.of(
                item("VALUE", new BigDecimal("30.00")),
                item("EFFORT", new BigDecimal("15.50")),
                item("RISK", new BigDecimal("6.00"))));

        service.applyStatusTransition(cycle, DemandPlanningStatus.PRIORITIZED,
                DemandPlanningStatus.IN_REVIEW, "Aguarda revisão da direção");

        assertThat(cycle.getScoreStatus()).isEqualTo(DemandScoreLifecycleService.SCORE_INVALID);
        assertThat(cycle.getScoreTotal()).isNull();
        assertThat(cycle.getScoreInvalidationReason()).isEqualTo("Aguarda revisão da direção");
        assertThat(cycle.getScoreInvalidatedAt()).isNotNull();

        JsonNode snapshot = service.previousScoreSnapshot(cycle);
        assertThat(snapshot.get("scoreTotal").decimalValue()).isEqualByComparingTo("51.50");
        assertThat(snapshot.get("scoreValue").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(snapshot.get("scoreEffort").decimalValue()).isEqualByComparingTo("15.50");
        assertThat(snapshot.get("scoreRisk").decimalValue()).isEqualByComparingTo("6.00");
        assertThat(snapshot.get("portfolioRank").asInt()).isEqualTo(3);

        ArgumentCaptor<DemandPlanningCycleScoreHistory> captor =
                ArgumentCaptor.forClass(DemandPlanningCycleScoreHistory.class);
        verify(scoreHistoryRepo).save(captor.capture());
        DemandPlanningCycleScoreHistory saved = captor.getValue();
        assertThat(saved.getScoreTotal()).isEqualByComparingTo("51.50");
        assertThat(saved.getScoreValue()).isEqualByComparingTo("30.00");
        assertThat(saved.getScoreEffort()).isEqualByComparingTo("15.50");
        assertThat(saved.getScoreRisk()).isEqualByComparingTo("6.00");
        assertThat(saved.getPortfolioRank()).isEqualTo(3);
        assertThat(saved.getInvalidationReason()).isEqualTo("Aguarda revisão da direção");
    }

    @Test
    void repeatedInvalidationOnAlreadyInvalidatedCycleDoesNotDuplicateHistory() {
        DemandPlanningCycle cycle = validScoredCycle(new BigDecimal("40.00"), 5);
        stubItems(cycle.getId(), List.of(item("VALUE", new BigDecimal("40.00"))));

        service.applyStatusTransition(cycle, DemandPlanningStatus.PRIORITIZED,
                DemandPlanningStatus.IN_REVIEW, "primeira revisão");
        service.applyStatusTransition(cycle, DemandPlanningStatus.IN_REVIEW,
                DemandPlanningStatus.IN_REVIEW, "outra tentativa");

        verify(scoreHistoryRepo, org.mockito.Mockito.times(1))
                .save(any(DemandPlanningCycleScoreHistory.class));
    }

    @Test
    void markCalculatedClearsSnapshotButKeepsPersistedHistory() {
        DemandPlanningCycle cycle = validScoredCycle(new BigDecimal("51.50"), 3);
        stubItems(cycle.getId(), List.of(item("VALUE", new BigDecimal("51.50"))));
        service.applyStatusTransition(cycle, DemandPlanningStatus.PRIORITIZED,
                DemandPlanningStatus.IN_REVIEW, "revisão");

        service.markCalculated(cycle);

        assertThat(cycle.getScoreStatus()).isEqualTo(DemandScoreLifecycleService.SCORE_VALID);
        assertThat(cycle.getPreviousScoreSnapshot()).isNull();
        assertThat(cycle.getScoreCalculatedAt()).isNotNull();
        assertThat(savedHistory).hasSize(1);
    }

    @Test
    void listHistoryReturnsEntriesFromRepository() {
        DemandPlanningCycle cycle = validScoredCycle(new BigDecimal("10.00"), 1);
        when(scoreHistoryRepo.findByPlanningCycle_IdOrderByInvalidatedAtDesc(cycle.getId()))
                .thenReturn(List.of(
                        historyEntry(new BigDecimal("42.00"), Instant.parse("2026-08-10T00:00:00Z"),
                                "segunda revisão"),
                        historyEntry(new BigDecimal("30.00"), Instant.parse("2026-07-01T00:00:00Z"),
                                "primeira revisão")));

        List<ScoreHistoryEntry> entries = service.listHistory(cycle.getId());

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).scoreTotal()).isEqualByComparingTo("42.00");
        assertThat(entries.get(0).invalidationReason()).isEqualTo("segunda revisão");
        assertThat(entries.get(1).scoreTotal()).isEqualByComparingTo("30.00");
    }

    @Test
    void invalidateSkipsCyclesWithoutScoreHistory() {
        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setStatus(DemandPlanningStatus.IN_ANALYSIS);
        cycle.setScoreStatus(DemandScoreLifecycleService.SCORE_NOT_CALCULATED);

        service.applyStatusTransition(cycle, DemandPlanningStatus.IN_ANALYSIS,
                DemandPlanningStatus.IN_REVIEW, "sem score ainda");

        assertThat(cycle.getScoreStatus()).isEqualTo(DemandScoreLifecycleService.SCORE_NOT_CALCULATED);
        verify(scoreHistoryRepo, never()).save(any(DemandPlanningCycleScoreHistory.class));
    }

    private DemandPlanningCycle validScoredCycle(BigDecimal total, int portfolioRank) {
        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setId(UUID.randomUUID());
        cycle.setStatus(DemandPlanningStatus.PRIORITIZED);
        cycle.setScoreStatus(DemandScoreLifecycleService.SCORE_VALID);
        cycle.setScoreTotal(total);
        cycle.setPortfolioRank(portfolioRank);
        cycle.setScoreCalculatedAt(Instant.parse("2026-06-01T10:00:00Z"));
        return cycle;
    }

    private void stubItems(UUID cycleId, List<DemandScoring> items) {
        lenient().when(scoring.findByPlanningCycle_IdOrderByCriterionOrderIndexAsc(cycleId)).thenReturn(items);
    }

    private DemandScoring item(String dimensionCode, BigDecimal weightedScore) {
        ScoringDimension dimension = new ScoringDimension();
        dimension.setId(UUID.randomUUID());
        dimension.setCode(dimensionCode);
        ScoringCriterion criterion = new ScoringCriterion();
        criterion.setId(UUID.randomUUID());
        criterion.setDimension(dimension);
        DemandScoring item = new DemandScoring();
        item.setCriterion(criterion);
        item.setWeightedScore(weightedScore);
        return item;
    }

    private DemandPlanningCycleScoreHistory historyEntry(BigDecimal total, Instant invalidatedAt, String reason) {
        DemandPlanningCycleScoreHistory h = new DemandPlanningCycleScoreHistory();
        h.setId(UUID.randomUUID());
        h.setScoreTotal(total);
        h.setPortfolioRank(1);
        h.setCalculatedAt(invalidatedAt.minusSeconds(3600));
        h.setInvalidatedAt(invalidatedAt);
        h.setInvalidationReason(reason);
        return h;
    }
}
