package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.DemandCounter;
import cvt.cv.ppmbackend.repository.DemandCounterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemandCodeService {
    private static final String COUNTER_KEY = "DEMAND_CODE";
    private final DemandCounterRepository counters;

    public DemandCodeService(DemandCounterRepository counters) {
        this.counters = counters;
    }

    @Transactional
    public String nextCode() {
        DemandCounter counter = counters.lockByKey(COUNTER_KEY).orElseGet(() -> {
            DemandCounter created = new DemandCounter();
            created.setCounterKey(COUNTER_KEY);
            created.setCounterValue(0L);
            return created;
        });
        counter.setCounterValue(counter.getCounterValue() + 1);
        counters.save(counter);
        return String.format("DEM-%06d", counter.getCounterValue());
    }
}
