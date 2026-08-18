package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectExecutionRankHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectExecutionRankHistoryRepository extends JpaRepository<ProjectExecutionRankHistory, UUID> {
    List<ProjectExecutionRankHistory> findByProject_IdOrderByChangedAtDesc(UUID projectId);
}
