package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.ProjectCreateRequest;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.repository.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService extends AbstractCrudService<Project, ProjectCreateRequest> {
    private final ProjectRepository projects;
    private final ProgramService programs;
    private final SupplierService suppliers;
    private final LookupValueService lookups;
    private OperationalPlanService operationalPlans;

    public ProjectService(ProjectRepository r, ProgramService p, SupplierService s, LookupValueService l) {
        super(r, "Projeto");
        projects = r;
        programs = p;
        suppliers = s;
        lookups = l;
    }

    @Autowired
    public void setOperationalPlans(@Lazy OperationalPlanService s) {
        this.operationalPlans = s;
    }

    protected Project newEntity() {
        return new Project();
    }

    protected Project apply(ProjectCreateRequest r, Project e) {
        validateDates(r.plannedStartDate(), r.plannedEndDate(), "Data planeada");
        validateDates(r.startDate(), r.endDate(), "Data real");
        e.setName(r.name());
        e.setDescription(r.description());
        e.setProgram(programs.findById(r.programId()));
        e.setOperationalPlan(r.operationalPlanId() == null ? null : operationalPlans.findById(r.operationalPlanId()));
        e.setDomain(r.domainId() == null ? null : lookups.findById(r.domainId()));
        e.setBusinessArea(r.businessArea());
        e.setProjectType(r.projectTypeId() == null ? null : lookups.findById(r.projectTypeId()));
        e.setResponsibleDirection(r.responsibleDirection());
        e.setResponsibleTeam(r.responsibleTeam());
        e.setProjectManager(r.projectManager());
        e.setStatus(r.status());
        e.setProjectPhase(r.projectPhaseId() == null ? null : lookups.findById(r.projectPhaseId()));
        e.setMainSupplier(r.mainSupplierId() == null ? null : suppliers.findById(r.mainSupplierId()));
        e.setImpactedSystem(r.impactedSystem());
        e.setScheduleStatus(r.scheduleStatus());
        e.setCostStatus(r.costStatus());
        e.setRiskStatus(r.riskStatus());
        e.setValueStatus(r.valueStatus());
        e.setExpectedBenefits(r.expectedBenefits());
        e.setPlannedStartDate(r.plannedStartDate());
        e.setStartDate(r.startDate());
        e.setPlannedEndDate(r.plannedEndDate());
        e.setEndDate(r.endDate());
        e.setPriority(r.priority());
        e.setRanking(r.ranking());
        e.setBudgetLine(r.budgetLine());
        e.setBudget(r.budget());
        e.setPlanType(r.planType());
        e.setDelayReasons(r.delayReasons());
        return e;
    }

    @Transactional(readOnly = true)
    public List<Project> findByStatus(ProjectStatus s) {
        return projects.findByStatus(s);
    }

    @Transactional(readOnly = true)
    public List<Project> findByDomain(UUID domainId) {
        return projects.findByDomainId(domainId);
    }

    @Transactional(readOnly = true)
    public List<Project> findByProgram(UUID id) {
        programs.findById(id);
        return projects.findByProgramId(id);
    }

    @Transactional(readOnly = true)
    public List<Project> findByOperationalPlan(UUID id) {
        return projects.findByOperationalPlan_Id(id);
    }
}
