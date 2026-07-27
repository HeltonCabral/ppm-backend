package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.CapacityAllocationCreateRequest;
import cvt.cv.ppmbackend.entity.*;
import cvt.cv.ppmbackend.enums.*;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.CapacityAllocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.YearMonth;
import java.util.*;

@Service
public class CapacityAllocationService
        extends AbstractCrudService<CapacityAllocation, CapacityAllocationCreateRequest> {
    private final CapacityAllocationRepository allocations;
    private final ProjectService projects;
    private final MacroResourceService resources;

    public CapacityAllocationService(CapacityAllocationRepository r, ProjectService p, MacroResourceService m) {
        super(r, "Alocação de capacidade");
        allocations = r;
        projects = p;
        resources = m;
    }

    protected CapacityAllocation newEntity() {
        return new CapacityAllocation();
    }

    protected CapacityAllocation apply(CapacityAllocationCreateRequest r, CapacityAllocation e) {
        Project p = projects.findById(r.projectId());
        MacroResource m = resources.findById(r.macroResourceId());
        if (m.getCapacityUnit() != r.capacityUnit())
            throw new BadRequestException("A unidade da alocação deve ser igual à unidade do recurso");
        e.setProject(p);
        e.setMacroResource(m);
        e.setPeriod(r.period());
        e.setAllocatedCapacity(r.allocatedCapacity());
        e.setCapacityUnit(r.capacityUnit());
        e.setAllocationStatus(calculateResourceUsage(m, r.period(), r.allocatedCapacity(), e.getId()));
        return e;
    }

    public AllocationStatus calculateResourceUsage(MacroResource m, YearMonth period, BigDecimal candidate,
            UUID excludedId) {
        BigDecimal used = allocations.findByMacroResourceIdAndPeriod(m.getId(), period).stream()
                .filter(a -> !Objects.equals(a.getId(), excludedId)).map(CapacityAllocation::getAllocatedCapacity)
                .reduce(BigDecimal.ZERO, BigDecimal::add).add(candidate);
        BigDecimal pct = used.multiply(BigDecimal.valueOf(100)).divide(m.getTotalCapacity(), 4, RoundingMode.HALF_UP);
        return pct.compareTo(BigDecimal.valueOf(100)) > 0 ? AllocationStatus.OVERLOADED
                : pct.compareTo(BigDecimal.valueOf(80)) >= 0 ? AllocationStatus.NEAR_LIMIT : AllocationStatus.NORMAL;
    }

    @Transactional(readOnly = true)
    public List<CapacityAllocation> findByProject(UUID id) {
        projects.findById(id);
        return allocations.findByProjectId(id);
    }

    @Transactional(readOnly = true)
    public List<CapacityAllocation> findByResource(UUID id) {
        resources.findById(id);
        return allocations.findByMacroResourceId(id);
    }
}
