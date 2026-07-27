package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DependencyCreateRequest;
import cvt.cv.ppmbackend.entity.Dependency;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.repository.DependencyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DependencyService extends AbstractCrudService<Dependency, DependencyCreateRequest> {
    private final DependencyRepository dependencies;
    private final ProjectService projects;

    public DependencyService(DependencyRepository r, ProjectService p) {
        super(r, "Dependência");
        dependencies = r;
        projects = p;
    }

    protected Dependency newEntity() {
        return new Dependency();
    }

    protected Dependency apply(DependencyCreateRequest r, Dependency e) {
        if (Objects.equals(r.sourceProjectId(), r.targetProjectId()))
            throw new BadRequestException("Um projeto não pode depender de si próprio");
        e.setType(r.type());
        e.setDescription(r.description());
        e.setSourceProject(projects.findById(r.sourceProjectId()));
        e.setTargetProject(r.targetProjectId() == null ? null : projects.findById(r.targetProjectId()));
        e.setStatus(r.status());
        e.setImpactLevel(r.impactLevel());
        return e;
    }

    @Transactional(readOnly = true)
    public List<Dependency> findByProject(UUID id) {
        projects.findById(id);
        return dependencies.findBySourceProjectId(id);
    }
}
