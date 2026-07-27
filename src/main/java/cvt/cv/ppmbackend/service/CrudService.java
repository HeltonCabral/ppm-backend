package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.BaseEntity;
import java.util.List;
import java.util.UUID;

public interface CrudService<E extends BaseEntity, R> {
    List<E> findAll();

    E findById(UUID id);

    E create(R request);

    E update(UUID id, R request);

    void delete(UUID id);
}
