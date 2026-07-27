package cvt.cv.ppmbackend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandHistory;
import cvt.cv.ppmbackend.repository.DemandHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
public class DemandHistoryService {
    private final DemandHistoryRepository history;
    private final ObjectMapper mapper;

    public DemandHistoryService(DemandHistoryRepository history, ObjectMapper mapper) {
        this.history = history;
        this.mapper = mapper;
    }

    @Transactional
    public DemandHistory log(Demand demand, String eventType, String previousStatus, String newStatus, String description,
            String actorId, String actorName, Map<String, Object> metadata) {
        DemandHistory entry = new DemandHistory();
        entry.setDemand(demand);
        entry.setEventType(eventType);
        entry.setPreviousStatus(previousStatus);
        entry.setNewStatus(newStatus);
        entry.setDescription(description);
        entry.setActorId(actorId);
        entry.setActorName(actorName);
        entry.setMetadata(toJson(metadata));
        return history.save(entry);
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty())
            return null;
        try {
            return mapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return metadata.toString();
        }
    }
}
