package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.*;
import cvt.cv.ppmbackend.dto.ProjectExecutionRankDtos.ReprioritizeRequest;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.LookupValueRepository;
import cvt.cv.ppmbackend.repository.ProjectExecutionRankHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProjectLifecycleServiceTest {
    @Autowired ProjectService projects;
    @Autowired ProjectExecutionService executions;
    @Autowired LookupValueRepository lookups;
    @Autowired ProjectExecutionRankHistoryRepository history;

    @Test
    void createsExtraPlanWithExecutionAndReprioritizesOnlyProjectRank() {
        LookupValue domain = new LookupValue();
        domain.setCategory("PROJECT_DOMAIN");
        domain.setCode("SECURITY_TEST");
        domain.setLabel("Segurança Teste");
        domain.setSortOrder(1);
        lookups.save(domain);

        ProjectResponse first = projects.createExtraPlan(request("Primeiro", "Segurança Teste"), "tester");
        ProjectResponse second = projects.createExtraPlan(request("Segundo", "SECURITY_TEST"), "tester");

        assertThat(first.origin()).isEqualTo("EXTRA_PLAN");
        assertThat(first.executionRank()).isEqualTo(1);
        assertThat(first.execution()).isNotNull();
        assertThat(first.execution().progress()).isZero();
        assertThat(first.execution().plannedStartDate()).isEqualTo(LocalDate.of(2026, 1, 10));

        ProjectExecutionResponse updated = executions.update(first.id(), new ProjectExecutionUpdateRequest(
                40, new BigDecimal("1200"), LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 11),
                LocalDate.of(2026, 3, 30), null, ExecutiveStatus.YELLOW, ExecutiveStatus.GREEN,
                ExecutiveStatus.RED, ExecutiveStatus.GREEN, RiskLevel.HIGH, "Dependência", "Em execução"), "tester");
        assertThat(updated.progress()).isEqualTo(40);
        assertThat(projects.findById(first.id()).getName()).isEqualTo("Primeiro");

        var result = projects.reprioritize(second.id(),
                new ReprioritizeRequest(1, ReprioritizationReason.CRITICAL_DEPENDENCY, "Dependência crítica"),
                "tester");
        assertThat(result.previousPosition()).isEqualTo(2);
        assertThat(projects.findById(second.id()).getExecutionRank()).isEqualTo(1);
        assertThat(projects.findById(first.id()).getExecutionRank()).isEqualTo(2);
        assertThat(history.findByProject_IdOrderByChangedAtDesc(second.id())).hasSize(1);
    }

    @Test
    void startsPlannedProjectAndRecordsActualStartDate() {
        LookupValue domain = new LookupValue();
        domain.setCategory("PROJECT_DOMAIN");
        domain.setCode("START_TEST");
        domain.setLabel("Iniciar Projeto Teste");
        domain.setSortOrder(1);
        lookups.save(domain);

        ProjectResponse created = projects.createExtraPlan(request("Projeto a iniciar", "START_TEST"), "creator");
        ProjectResponse started = projects.start(created.id(), "starter");

        assertThat(started.status()).isEqualTo(ProjectStatus.IN_PROGRESS.name());
        assertThat(started.execution().actualStartDate()).isEqualTo(LocalDate.now());
        assertThat(started.execution().lastUpdatedBy()).isEqualTo("starter");
        assertThat(projects.findById(created.id()).getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);

        assertThatThrownBy(() -> projects.start(created.id(), "starter"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Apenas projetos planeados podem ser iniciados");
    }

    @Test
    void completesProjectInProgressAndRecordsActualEndDate() {
        LookupValue domain = new LookupValue();
        domain.setCategory("PROJECT_DOMAIN");
        domain.setCode("COMPLETE_TEST");
        domain.setLabel("Concluir Projeto Teste");
        domain.setSortOrder(1);
        lookups.save(domain);

        ProjectResponse created = projects.createExtraPlan(request("Projeto a concluir", "COMPLETE_TEST"), "creator");
        projects.start(created.id(), "starter");
        ProjectResponse completed = projects.complete(created.id(), "finisher");

        assertThat(completed.status()).isEqualTo(ProjectStatus.COMPLETED.name());
        assertThat(completed.execution().actualEndDate()).isEqualTo(LocalDate.now());
        assertThat(completed.execution().progress()).isEqualTo(100);
        assertThat(completed.execution().lastUpdatedBy()).isEqualTo("finisher");
        assertThat(projects.findById(created.id()).getStatus()).isEqualTo(ProjectStatus.COMPLETED);

        assertThatThrownBy(() -> projects.complete(created.id(), "finisher"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Apenas projetos em progresso podem ser concluídos");
    }

    private ProjectCreateExtraPlanRequest request(String name, String domain) {
        return new ProjectCreateExtraPlanRequest(name, "Descrição", "DSI", "Direção de Sistemas", "SEG",
                "Segurança", domain, null, "Firewall", "Redução de risco", new BigDecimal("2500000"),
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 3, 30), "Necessidade urgente não prevista");
    }
}
