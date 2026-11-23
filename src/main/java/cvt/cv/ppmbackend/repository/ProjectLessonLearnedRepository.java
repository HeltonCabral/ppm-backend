package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.ProjectLessonLearned;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectLessonLearnedRepository extends JpaRepository<ProjectLessonLearned, UUID> {
    List<ProjectLessonLearned> findByProjectIdOrderBySequenceAsc(UUID projectId);

    List<ProjectLessonLearned> findAllByOrderByCreatedAtDescSequenceAsc();
}
