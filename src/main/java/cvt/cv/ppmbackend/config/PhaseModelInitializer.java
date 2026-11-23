package cvt.cv.ppmbackend.config;

import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.repository.PhaseModelRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
public class PhaseModelInitializer implements ApplicationRunner {

    private final PhaseModelRepository models;

    public PhaseModelInitializer(PhaseModelRepository models) {
        this.models = models;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("FORMACAO", "Formação",
                "Modelo de fases aplicável a ações de formação.",
                List.of(
                        phase("PREP", "Preparação", 1, "20.00"),
                        phase("EXEC", "Execução", 2, "60.00"),
                        phase("AVAL", "Avaliação", 3, "20.00")));

        seed("PROJ_TI", "Projeto de TI",
                "Modelo de fases para projetos de tecnologias de informação.",
                List.of(
                        phase("INIT", "Iniciação", 1, "10.00"),
                        phase("PLAN", "Planeamento", 2, "15.00"),
                        phase("ANAL", "Análise", 3, "15.00"),
                        phase("DEV", "Desenvolvimento", 4, "30.00"),
                        phase("TEST", "Testes", 5, "15.00"),
                        phase("IMPL", "Implementação", 6, "10.00"),
                        phase("CLOSE", "Encerramento", 7, "5.00")));

        seed("PROJ_NEG", "Projeto de Negócio",
                "Modelo de fases para projetos de negócio.",
                List.of(
                        phase("INIT", "Iniciação", 1, "15.00"),
                        phase("PLAN", "Planeamento", 2, "20.00"),
                        phase("EXEC", "Execução", 3, "50.00"),
                        phase("CTRL", "Controlo", 4, "10.00"),
                        phase("CLOSE", "Encerramento", 5, "5.00")));

        seed("PROC_LOG", "Processo Logístico",
                "Modelo de fases para processos logísticos.",
                List.of(
                        phase("PLAN", "Planeamento", 1, "20.00"),
                        phase("APROV", "Aprovisionamento", 2, "30.00"),
                        phase("EXEC", "Execução", 3, "30.00"),
                        phase("CTRL", "Controlo", 4, "15.00"),
                        phase("CLOSE", "Encerramento", 5, "5.00")));

        seed("INFRA_TI", "Infraestrutura TI",
                "Modelo de fases para iniciativas de infraestrutura de TI.",
                List.of(
                        phase("INIT", "Iniciação", 1, "10.00"),
                        phase("DESIGN", "Desenho", 2, "20.00"),
                        phase("APROV", "Aprovisionamento", 3, "15.00"),
                        phase("IMPL", "Implementação", 4, "35.00"),
                        phase("TEST", "Testes", 5, "15.00"),
                        phase("CLOSE", "Encerramento", 6, "5.00")));

        seed("ENG_TELCO", "Projeto de Engenharia Telco",
                "Modelo de fases para projetos de engenharia de telecomunicações.",
                List.of(
                        phase("INIT", "Iniciação", 1, "10.00"),
                        phase("STUDY", "Estudo e Desenho", 2, "20.00"),
                        phase("PROC", "Aprovisionamento", 3, "15.00"),
                        phase("IMPL", "Implementação/Instalação", 4, "30.00"),
                        phase("COMM", "Testes e Comissionamento", 5, "15.00"),
                        phase("OPER", "Entrada em Operação", 6, "5.00"),
                        phase("CLOSE", "Encerramento", 7, "5.00")));
    }

    private void seed(String code, String name, String description, List<PhaseDefinition> phases) {
        if (models.existsByCodeIgnoreCase(code)) {
            return;
        }
        PhaseModel model = new PhaseModel();
        model.setCode(code);
        model.setName(name);
        model.setDescription(description);
        model.setActive(true);
        for (PhaseDefinition phase : phases) {
            phase.setPhaseModel(model);
            model.getPhases().add(phase);
        }
        models.save(model);
    }

    private PhaseDefinition phase(String code, String name, int sequence, String weight) {
        PhaseDefinition phase = new PhaseDefinition();
        phase.setCode(code);
        phase.setName(name);
        phase.setSequence(sequence);
        phase.setDurationWeightPercentage(new BigDecimal(weight));
        phase.setActive(true);
        return phase;
    }
}
