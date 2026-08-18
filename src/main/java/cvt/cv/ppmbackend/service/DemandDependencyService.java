package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDependencyDtos.CreateRequest;
import cvt.cv.ppmbackend.dto.DemandDependencyDtos.Response;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandDependency;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandDependencyRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class DemandDependencyService {
    private final DemandDependencyRepository dependencies;
    private final DemandRepository demands;
    private final EntityManager entityManager;

    public DemandDependencyService(DemandDependencyRepository dependencies, DemandRepository demands,
            EntityManager entityManager) {
        this.dependencies = dependencies;
        this.demands = demands;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<Response> list(UUID demandId) {
        requireDemand(demandId);
        return dependencies.findByDemand_IdOrderByCreatedAtAsc(demandId).stream()
                .map(this::response)
                .toList();
    }

    public Response create(UUID demandId, CreateRequest request, String actor) {
        if (demandId.equals(request.dependsOnDemandId())) {
            throw new BadRequestException("Uma Demanda não pode depender dela própria");
        }

        Demand demand = requireDemand(demandId);
        Demand target = requireDemand(request.dependsOnDemandId());
        lockInStableOrder(demand, target);

        if (dependencies.existsByDemand_IdAndDependsOnDemand_Id(demandId, request.dependsOnDemandId())) {
            throw new BadRequestException("A dependência entre as Demandas já existe");
        }
        if (reaches(request.dependsOnDemandId(), demandId)) {
            throw new BadRequestException("A dependência criaria uma relação circular entre Demandas");
        }

        DemandDependency dependency = new DemandDependency();
        dependency.setDemand(demand);
        dependency.setDependsOnDemand(target);
        dependency.setType(request.type());
        dependency.setDescription(request.description());
        dependency.setCreatedBy(actor(actor));

        DemandDependency saved = dependencies.saveAndFlush(dependency);
        refreshDependencyMetrics(demand, actor);
        return response(saved);
    }

    public void delete(UUID demandId, UUID dependencyId, String actor) {
        Demand demand = requireDemand(demandId);
        DemandDependency dependency = dependencies.findByIdAndDemand_Id(dependencyId, demandId)
                .orElseThrow(() -> new ResourceNotFoundException("Dependência não encontrada: " + dependencyId));

        dependencies.delete(dependency);
        dependencies.flush();
        refreshDependencyMetrics(demand, actor);
    }

    private boolean reaches(UUID startId, UUID expectedId) {
        Set<UUID> visited = new HashSet<>();
        ArrayDeque<UUID> pending = new ArrayDeque<>();
        pending.add(startId);

        while (!pending.isEmpty()) {
            Set<UUID> level = new HashSet<>();
            while (!pending.isEmpty()) {
                UUID current = pending.removeFirst();
                if (visited.add(current)) {
                    level.add(current);
                }
            }
            if (level.isEmpty()) {
                continue;
            }

            List<UUID> targets = dependencies.findTargetIdsByDemandIds(level);
            if (targets.contains(expectedId)) {
                return true;
            }
            targets.stream().filter(target -> !visited.contains(target)).forEach(pending::addLast);
        }
        return false;
    }

    private void lockInStableOrder(Demand first, Demand second) {
        List<Demand> ordered = first.getId().compareTo(second.getId()) < 0
                ? List.of(first, second)
                : List.of(second, first);
        ordered.forEach(demand -> entityManager.lock(demand, LockModeType.PESSIMISTIC_WRITE));
    }

    private void refreshDependencyMetrics(Demand demand, String actor) {
        demand.setDependenciesCount(Math.toIntExact(dependencies.countByDemand_Id(demand.getId())));
        demand.setComplexityScore(null);
        demand.setComplexity(null);
        demand.setEstimatedDurationMonths(null);
        demand.setPlannedStartDate(null);
        demand.setUpdatedBy(actor(actor));
        demands.save(demand);
    }

    private Demand requireDemand(UUID id) {
        return demands.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demanda não encontrada: " + id));
    }

    private Response response(DemandDependency dependency) {
        Demand demand = dependency.getDemand();
        Demand target = dependency.getDependsOnDemand();
        return new Response(dependency.getId(), demand.getId(), target.getId(), target.getCode(), target.getTitle(),
                dependency.getType(), dependency.getDescription());
    }

    private String actor(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }
}
