package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, UUID> {
    List<NotificationRead> findByUserName(String userName);
    List<NotificationRead> findByUserNameAndAlertIdIn(String userName, Collection<String> alertIds);
}
