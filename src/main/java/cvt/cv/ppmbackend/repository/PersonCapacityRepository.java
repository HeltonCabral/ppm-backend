package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.PersonCapacity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PersonCapacityRepository extends JpaRepository<PersonCapacity, UUID> {
    List<PersonCapacity> findAllByOrderByUserNameAscValidFromAsc();

    List<PersonCapacity> findByUserNameOrderByValidFromAsc(String userName);

    List<PersonCapacity> findByUserNameIn(Collection<String> userNames);
}
