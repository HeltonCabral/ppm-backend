package cvt.cv.ppmbackend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import cvt.cv.ppmbackend.dto.ProjectScoringCreateRequest;
import cvt.cv.ppmbackend.service.ProjectScoringService;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PpmbackendApplicationTests {
	@Autowired
	private ProjectScoringService service;

	@Test
	void contextLoads() {
	}

	@Test
	void calculatesFinalScoreFromValueEffortAndRisk() {
		var request = new ProjectScoringCreateRequest(
				UUID.randomUUID(), 5, 4, 3, 2, 2, 2, 1, 1, 1);

		assertThat(service.calculateFinalScore(request)).isEqualByComparingTo(new BigDecimal("1.20"));
	}
}
