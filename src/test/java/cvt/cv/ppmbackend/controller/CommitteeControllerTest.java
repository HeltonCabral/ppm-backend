package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.repository.CommitteeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CommitteeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CommitteeRepository committees;

    @BeforeEach
    void cleanDatabase() {
        committees.deleteAll();
    }

    @Test
    void createsAndReturnsTheExpectedContract() throws Exception {
        mockMvc.perform(post("/committees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("  Comité Executivo  ")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Comité Executivo"))
                .andExpect(jsonPath("$.description").value("Decide prioridades."))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.isStrategicCommittee").value(true))
                .andExpect(jsonPath("$.members", hasSize(2)))
                .andExpect(jsonPath("$.members[0].name").value("CFO"))
                .andExpect(jsonPath("$.members[0].code").value("cfo-001"))
                .andExpect(jsonPath("$.directions[0]").value("Financeira"))
                .andExpect(jsonPath("$.demandTypes[0]").value("Inovação"))
                .andExpect(jsonPath("$.domains[0]").value("Digital"))
                .andExpect(jsonPath("$.riskLevels[0]").value("Alto"))
                .andExpect(jsonPath("$.minimumBudget").value(1000.00))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    @Test
    void rejectsCaseInsensitiveDuplicateNames() throws Exception {
        mockMvc.perform(post("/committees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("Comité Executivo")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/committees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("comité executivo")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMITTEE_NAME_CONFLICT"))
                .andExpect(jsonPath("$.message").value("Já existe um comité com o nome 'comité executivo'."));
    }

    @Test
    void rejectsDuplicateArrayValuesAfterTrimmingAndIgnoringCase() throws Exception {
        String body = """
                {
                  "name": "Comité Executivo",
                  "description": "Decide prioridades.",
                  "status": "ACTIVE",
                  "isStrategicCommittee": true,
                                                                        "members": [
                                                                                { "name": "CFO", "code": "cfo-001" },
                                                                                { "name": "Financeiro", "code": "cfo-001" }
                                                                        ],
                  "directions": [],
                  "demandTypes": [],
                  "domains": [],
                  "riskLevels": [],
                  "minimumBudget": null
                }
                """;

        mockMvc.perform(post("/committees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("A lista de membros não pode conter códigos duplicados."));
    }

    @Test
    void rejectsEmptyArrayValues() throws Exception {
        String body = """
                {
                  "name": "Comité Executivo",
                  "description": "Decide prioridades.",
                  "status": "ACTIVE",
                  "isStrategicCommittee": true,
                                                                        "members": [
                                                                                { "name": "", "code": "cfo-001" }
                                                                        ],
                  "directions": [],
                  "demandTypes": [],
                  "domains": [],
                  "riskLevels": [],
                  "minimumBudget": null
                }
                """;

        mockMvc.perform(post("/committees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$['fieldErrors']['members[0].name']")
                        .value("O nome do membro é obrigatório."));
    }

    @Test
    void rejectsUnsupportedStatus() throws Exception {
        String body = validRequest("Comité Executivo").replace("ACTIVE", "ARCHIVED");

        mockMvc.perform(post("/committees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("O estado deve ser ACTIVE ou INACTIVE."));
    }

    @Test
    void rejectsNegativeMinimumBudget() throws Exception {
        String body = validRequest("Comité Executivo").replace("1000.00", "-0.01");

        mockMvc.perform(post("/committees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$['fieldErrors']['minimumBudget']")
                        .value("O orçamento mínimo deve ser maior ou igual a zero."));
    }

    @Test
    void updatesListsAndDeletesCommittee() throws Exception {
        String location = mockMvc.perform(post("/committees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("Comité Executivo")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        String id = location.substring(location.lastIndexOf('/') + 1);
        String updatedBody = """
                {
                  "name": "Comité de Portfólio",
                  "description": "Descrição atualizada.",
                  "status": "INACTIVE",
                  "isStrategicCommittee": false,
                                                                        "members": [
                                                                                { "name": "PMO", "code": "pmo-001" }
                                                                        ],
                  "directions": ["Estratégia"],
                  "demandTypes": ["Melhoria"],
                  "domains": ["Sistemas"],
                  "riskLevels": ["Crítico"],
                  "minimumBudget": 2500.00
                }
                """;

        mockMvc.perform(put("/committees/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Comité de Portfólio"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.isStrategicCommittee").value(false))
                .andExpect(jsonPath("$.domains[0]").value("Sistemas"))
                .andExpect(jsonPath("$.riskLevels[0]").value("Crítico"))
                .andExpect(jsonPath("$.minimumBudget").value(2500.00))
                .andExpect(jsonPath("$.members", hasSize(1)));

        mockMvc.perform(get("/committees/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directions[0]").value("Estratégia"));

        mockMvc.perform(delete("/committees/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/committees/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForUnknownCommittee() throws Exception {
        mockMvc.perform(get("/committees/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    private String validRequest(String name) {
        return """
                {
                  "name": "%s",
                  "description": "Decide prioridades.",
                  "status": "ACTIVE",
                  "isStrategicCommittee": true,
                                                                        "members": [
                                                                                { "name": "CFO", "code": "cfo-001" },
                                                                                { "name": "CTO", "code": "cto-001" }
                                                                        ],
                  "directions": ["Financeira"],
                  "demandTypes": ["Inovação"],
                  "domains": ["Digital"],
                  "riskLevels": ["Alto"],
                  "minimumBudget": 1000.00
                }
                """.formatted(name);
    }
}
