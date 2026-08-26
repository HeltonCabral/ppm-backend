package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandDtos.DemandSuccessCriterionInput;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandSuccessCriterionResponse;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandSuccessCriterion;
import cvt.cv.ppmbackend.repository.DemandSuccessCriterionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DemandSuccessCriterionService {
    private final DemandSuccessCriterionRepository criteria;

    public DemandSuccessCriterionService(DemandSuccessCriterionRepository criteria) {
        this.criteria = criteria;
    }

    public void sync(Demand demand, List<DemandSuccessCriterionInput> inputs) {
        if (inputs == null) return;

        criteria.deleteByDemandId(demand.getId());
        List<DemandSuccessCriterion> created = new java.util.ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            DemandSuccessCriterion criterion = new DemandSuccessCriterion();
            criterion.setDemand(demand);
            criterion.setDescription(inputs.get(i).description());
            criterion.setDisplayOrder(i);
            created.add(criterion);
        }
        criteria.saveAll(created);
    }

    @Transactional(readOnly = true)
    public List<DemandSuccessCriterionResponse> list(UUID demandId) {
        return criteria.findByDemandIdOrderByDisplayOrderAsc(demandId).stream()
                .map(c -> new DemandSuccessCriterionResponse(c.getId(), c.getDescription(), c.getDisplayOrder()))
                .toList();
    }
}
