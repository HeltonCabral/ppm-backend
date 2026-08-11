package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.Committee;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommitteeRepository extends JpaRepository<Committee, UUID> {
    List<Committee> findAllByOrderByNameAsc();

    List<Committee> findByStatusOrderByNameAsc(CommitteeStatus status);

        @Query("""
            SELECT c
            FROM Committee c
            JOIN c.members m
            WHERE LOWER(m.code) = LOWER(:username)
            ORDER BY c.name ASC
            """)
        List<Committee> findByMemberCode(@Param("username") String username);

    boolean existsByNameKey(String nameKey);

    boolean existsByNameKeyAndIdNot(String nameKey, UUID id);
}
