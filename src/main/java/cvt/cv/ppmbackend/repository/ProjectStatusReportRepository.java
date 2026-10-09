package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectStatusReport;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectStatusReportRepository extends JpaRepository<ProjectStatusReport, UUID> {
    List<ProjectStatusReport> findByProjectIdOrderByPeriodEndDescCreatedAtDesc(UUID projectId);
}
