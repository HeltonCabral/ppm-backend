package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.UserDirection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDirectionRepository extends JpaRepository<UserDirection, UUID> {

    List<UserDirection> findAllByOrderByUserNameAscDirectionCodeAsc();

    Optional<UserDirection> findByUserNameAndDirectionCode(String userName, String directionCode);

    /** Atribuições em vigor: {@code validUntil} nulo ou não anterior a {@code today}. */
    @Query("select u from UserDirection u where u.userName = :userName "
            + "and (u.validUntil is null or u.validUntil >= :today) order by u.directionCode")
    List<UserDirection> findActiveByUserName(@Param("userName") String userName, @Param("today") LocalDate today);
}
