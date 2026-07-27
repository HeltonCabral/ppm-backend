package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.BaseEntity;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Transactional
public abstract class AbstractCrudService<E extends BaseEntity, R> implements CrudService<E, R> {
    protected final JpaRepository<E, UUID> repository;
    private final String label;

    protected AbstractCrudService(JpaRepository<E, UUID> repository, String label) {
        this.repository = repository;
        this.label = label;
    }

    @Transactional(readOnly = true)
    public List<E> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public E findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(label + " não encontrado: " + id));
    }

    public E create(R request) {
        return repository.save(apply(request, newEntity()));
    }

    public E update(UUID id, R request) {
        return repository.save(apply(request, findById(id)));
    }

    public void delete(UUID id) {
        repository.delete(findById(id));
    }

    protected abstract E newEntity();

    protected abstract E apply(R request, E entity);

    protected void validateDates(java.time.LocalDate start, java.time.LocalDate end, String label) {
        if (start != null && end != null && end.isBefore(start))
            throw new cvt.cv.ppmbackend.exception.BadRequestException(label + " final não pode ser anterior à inicial");
    }
}
