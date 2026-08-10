package cvt.cv.ppmbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import cvt.cv.ppmbackend.entity.Committee;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.enums.CommitteeStatus;
import cvt.cv.ppmbackend.repository.CommitteeRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.LookupValueRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DemandCommitteeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommitteeRepository committees;

    @Autowired
    private LookupValueRepository lookupValues;

    @Autowired
    private DemandRepository demands;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createsDemandAssignedToCommitteeAndReturnsItsSummary() throws Exception {
        LookupValue type = demandType();
        Committee committee = committee();

        String request = """
                {
                  "title": "Modernizar atendimento",
                  "typeId": "%s",
                  "committeeId": "%s"
                }
                """.formatted(type.getId(), committee.getId());

        mockMvc.perform(post("/demands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.committeeId").value(committee.getId().toString()))
                .andExpect(jsonPath("$.suggestedCommitteeId").value(committee.getId().toString()))
                .andExpect(jsonPath("$.responsibleCommitteeId").value(committee.getId().toString()))
                .andExpect(jsonPath("$.committee.id").value(committee.getId().toString()))
                .andExpect(jsonPath("$.committee.name").value("Comité Executivo"))
                .andExpect(jsonPath("$.committee.status").value("ACTIVE"))
                .andExpect(jsonPath("$.committee.isStrategicCommittee").value(true));
    }

    @Test
    void rejectsUnknownCommittee() throws Exception {
        LookupValue type = demandType();
        UUID committeeId = UUID.randomUUID();

        String request = """
                {
                  "title": "Modernizar atendimento",
                  "typeId": "%s",
                  "committeeId": "%s"
                }
                """.formatted(type.getId(), committeeId);

        mockMvc.perform(post("/demands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Comité não encontrado: " + committeeId));
    }

    @Test
    void suggestsAppliesAndConfirmsResponsibleCommittee() throws Exception {
        LookupValue type = demandType();
        LookupValue domain = domain();

        Committee best = committee("Comité Digital");
        best.getDirections().add("Financeira");
        best.getDemandTypes().add("Melhoria");
        best.getDomains().add("Digital");
        best.getRiskLevels().add("Alto");
        best.setMinimumBudget(new BigDecimal("1000.00"));
        committees.save(best);

        Committee alternative = committee("Comité Financeiro");
        alternative.getDirections().add("Financeira");
        committees.save(alternative);

        Committee inactive = committee("Comité Inativo");
        inactive.setStatus(CommitteeStatus.INACTIVE);
        inactive.getDirections().add("Financeira");
        inactive.getDemandTypes().add("Melhoria");
        inactive.getDomains().add("Digital");
        inactive.getRiskLevels().add("Alto");
        inactive.setMinimumBudget(BigDecimal.ZERO);
        committees.save(inactive);

        String createRequest = """
                {
                  "title": "Modernizar atendimento",
                  "direction": "Financeira",
                  "typeId": "%s",
                  "domainId": "%s",
                  "riskStatus": "HIGH",
                  "estimatedBudget": 5000.00
                }
                """.formatted(type.getId(), domain.getId());
        String responseBody = mockMvc.perform(post("/demands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID demandId = UUID.fromString(objectMapper.readTree(responseBody).path("id").asText());

        mockMvc.perform(get("/demands/{id}/committee-suggestion", demandId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedCommitteeId").value(best.getId().toString()))
                .andExpect(jsonPath("$.suggestedCommitteeNameKey").value(best.getNameKey()))
                .andExpect(jsonPath("$.score").value(130))
                .andExpect(jsonPath("$.reasons", hasSize(5)))
                .andExpect(jsonPath("$.alternatives", hasSize(1)))
                .andExpect(jsonPath("$.alternatives[0].committeeId").value(alternative.getId().toString()))
                .andExpect(jsonPath("$.alternatives[0].score").value(20));

        mockMvc.perform(post("/demands/{id}/apply-committee-suggestion", demandId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedCommitteeId").value(best.getId().toString()));

        mockMvc.perform(get("/demands/{id}", demandId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedCommitteeId").value(best.getId().toString()))
                .andExpect(jsonPath("$.responsibleCommitteeId").value(best.getId().toString()));

        mockMvc.perform(post("/demands/{id}/confirm-committee", demandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "committeeId": "%s" }
                                """.formatted(inactive.getId())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMMITTEE_INACTIVE"));

        Committee confirmed = committees.save(committee("Comité Executivo"));
        String confirmationWithoutReason = """
                { "committeeId": "%s" }
                """.formatted(confirmed.getId());
        mockMvc.perform(post("/demands/{id}/confirm-committee", demandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationWithoutReason))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COMMITTEE_CHANGE_JUSTIFICATION_REQUIRED"));

        String confirmation = """
                {
                  "committeeId": "%s",
                  "justification": "Escalonamento executivo."
                }
                """.formatted(confirmed.getId());
        mockMvc.perform(post("/demands/{id}/confirm-committee", demandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmation))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedCommitteeId").value(best.getId().toString()))
                .andExpect(jsonPath("$.responsibleCommitteeId").value(confirmed.getId().toString()))
                .andExpect(jsonPath("$.committeeChangeJustification").value("Escalonamento executivo."));

        mockMvc.perform(delete("/committees/{id}", confirmed.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMITTEE_IN_USE"));

        Demand demand = demands.findByIdAndDeletedAtIsNull(demandId).orElseThrow();
        demand.setStatus("UNDER_PRIORITIZATION");
        demands.saveAndFlush(demand);
        mockMvc.perform(patch("/demands/{id}/status", demandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "READY_FOR_COMMITTEE" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_COMMITTEE"));
    }

    @Test
    void returnsNoSuggestionWhenEveryScoreIsZero() throws Exception {
        LookupValue type = demandType();
        committees.save(committee("Sem correspondência"));
        String responseBody = mockMvc.perform(post("/demands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Demanda sem correspondência",
                                  "typeId": "%s"
                                }
                                """.formatted(type.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID demandId = UUID.fromString(objectMapper.readTree(responseBody).path("id").asText());

        mockMvc.perform(get("/demands/{id}/committee-suggestion", demandId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedCommitteeId").doesNotExist())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.alternatives", hasSize(0)));
    }

    @Test
    void requiresResponsibleCommitteeBeforeReadyForCommittee() throws Exception {
        LookupValue type = demandType();
        String responseBody = mockMvc.perform(post("/demands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Demanda sem comité",
                                  "typeId": "%s"
                                }
                                """.formatted(type.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID demandId = UUID.fromString(objectMapper.readTree(responseBody).path("id").asText());
        Demand demand = demands.findByIdAndDeletedAtIsNull(demandId).orElseThrow();
        demand.setStatus("UNDER_PRIORITIZATION");
        demands.saveAndFlush(demand);

        mockMvc.perform(patch("/demands/{id}/status", demandId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "READY_FOR_COMMITTEE" }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RESPONSIBLE_COMMITTEE_REQUIRED"));
    }

    private LookupValue demandType() {
        LookupValue type = new LookupValue();
        type.setCategory("DEMAND_TYPE");
        type.setCode("IMPROVEMENT-" + UUID.randomUUID());
        type.setLabel("Melhoria");
        type.setActive(true);
        return lookupValues.save(type);
    }

    private LookupValue domain() {
        LookupValue domain = new LookupValue();
        domain.setCategory("DEMAND_DOMAIN");
        domain.setCode("DIGITAL-" + UUID.randomUUID());
        domain.setLabel("Digital");
        domain.setActive(true);
        return lookupValues.save(domain);
    }

    private Committee committee() {
        return committee("Comité Executivo");
    }

    private Committee committee(String name) {
        Committee committee = new Committee();
        committee.setName(name);
        committee.setNameKey(name.toLowerCase().replace(' ', '-') + "-" + UUID.randomUUID());
        committee.setDescription("Decide prioridades.");
        committee.setStatus(CommitteeStatus.ACTIVE);
        committee.setStrategicCommittee(true);
        return committees.save(committee);
    }
}
