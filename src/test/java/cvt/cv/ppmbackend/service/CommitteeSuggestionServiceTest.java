package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CommitteeSuggestionResponse;
import cvt.cv.ppmbackend.entity.Committee;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import cvt.cv.ppmbackend.repository.CommitteeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommitteeSuggestionServiceTest {

    private final CommitteeRepository committees = mock(CommitteeRepository.class);
    private final CommitteeSuggestionService service = new CommitteeSuggestionService(committees);

    @Test
    void scoresEveryConfiguredCriterionAndOrdersAlternatives() {
        Committee completeMatch = committee("Comité Digital");
        completeMatch.getDirections().add("Financeira");
        completeMatch.getDemandTypes().add("Inovação");
        completeMatch.getDomains().add("Digital");
        completeMatch.getRiskLevels().add("Alto");
        completeMatch.setMinimumBudget(new BigDecimal("1000.00"));

        Committee directionOnly = committee("Comité Financeiro");
        directionOnly.getDirections().add("financeira");

        when(committees.findByStatusOrderByNameAsc(CommitteeStatus.ACTIVE))
                .thenReturn(List.of(directionOnly, completeMatch));

        CommitteeSuggestionResponse response = service.suggest(demand());

        assertThat(response.suggestedCommitteeId()).isEqualTo(completeMatch.getId());
        assertThat(response.score()).isEqualTo(130);
        assertThat(response.reasons()).hasSize(5);
        assertThat(response.alternatives()).singleElement().satisfies(alternative -> {
            assertThat(alternative.committeeId()).isEqualTo(directionOnly.getId());
            assertThat(alternative.score()).isEqualTo(20);
        });
        verify(committees).findByStatusOrderByNameAsc(CommitteeStatus.ACTIVE);
    }

    @Test
    void usesStableNameOrderingForTies() {
        Committee secondByName = committee("Comité B");
        secondByName.getDomains().add("Digital");
        Committee firstByName = committee("Comité A");
        firstByName.getDomains().add("Digital");
        when(committees.findByStatusOrderByNameAsc(CommitteeStatus.ACTIVE))
                .thenReturn(List.of(secondByName, firstByName));

        CommitteeSuggestionResponse response = service.suggest(demand());

        assertThat(response.suggestedCommitteeId()).isEqualTo(firstByName.getId());
        assertThat(response.score()).isEqualTo(40);
        assertThat(response.alternatives()).hasSize(1);
        assertThat(response.alternatives().get(0).committeeId()).isEqualTo(secondByName.getId());
    }

    @Test
    void returnsEmptySuggestionWhenEveryScoreIsZero() {
        when(committees.findByStatusOrderByNameAsc(CommitteeStatus.ACTIVE))
                .thenReturn(List.of(committee("Sem correspondência")));

        CommitteeSuggestionResponse response = service.suggest(demand());

        assertThat(response.suggestedCommitteeId()).isNull();
        assertThat(response.suggestedCommitteeName()).isNull();
        assertThat(response.score()).isZero();
        assertThat(response.reasons()).isEmpty();
        assertThat(response.alternatives()).isEmpty();
    }

    @Test
    void ignoresStrategicCommittees() {
        Committee strategic = committee("Comité Estratégico", true);
        strategic.getDomains().add("Digital");

        Committee regular = committee("Comité Regular");
        regular.getDomains().add("Digital");

        when(committees.findByStatusOrderByNameAsc(CommitteeStatus.ACTIVE))
                .thenReturn(List.of(strategic, regular));

        CommitteeSuggestionResponse response = service.suggest(demand());

        assertThat(response.suggestedCommitteeId()).isEqualTo(regular.getId());
        assertThat(response.alternatives()).isEmpty();
    }

    private Demand demand() {
        Demand demand = new Demand();
        demand.setDirection("Financeira");
        demand.setRiskStatus("HIGH");
        demand.setEstimatedBudget(new BigDecimal("5000.00"));

        LookupValue type = new LookupValue();
        type.setCode("INNOVATION");
        type.setLabel("Inovação");
        demand.setType(type);

        LookupValue domain = new LookupValue();
        domain.setCode("DIGITAL");
        domain.setLabel("Digital");
        demand.setDomain(domain);
        return demand;
    }

    private Committee committee(String name) {
        return committee(name, false);
    }

    private Committee committee(String name, boolean strategic) {
        Committee committee = new Committee();
        committee.setId(UUID.randomUUID());
        committee.setName(name);
        committee.setNameKey(name.toLowerCase().replace(' ', '-'));
        committee.setStatus(CommitteeStatus.ACTIVE);
        committee.setStrategicCommittee(strategic);
        return committee;
    }
}
