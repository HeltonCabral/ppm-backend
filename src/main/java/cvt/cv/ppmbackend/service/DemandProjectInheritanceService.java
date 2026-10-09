package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandArea;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirectionArea;
import cvt.cv.ppmbackend.entity.DemandProfileRequirement;
import cvt.cv.ppmbackend.entity.DemandSuccessCriterion;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.entity.Project;
import cvt.cv.ppmbackend.entity.ProjectArea;
import cvt.cv.ppmbackend.entity.ProjectProfile;
import cvt.cv.ppmbackend.entity.ProjectSuccessCriterion;
import cvt.cv.ppmbackend.enums.AreaRole;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.enums.Priority;
import cvt.cv.ppmbackend.repository.DemandAreaRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionAreaRepository;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import cvt.cv.ppmbackend.repository.DemandSuccessCriterionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Passa para o projeto os dados da Demanda de origem que continuam a ser úteis na execução: enquadramento
 * (sponsor, requerente, problema, resposta proposta, riscos, dependências), Tipo e campos do Tipo,
 * natureza, complexidade, prazo obrigatório, fornecedores, critérios de sucesso, perfis previstos (onde
 * depois se associam as pessoas da equipa) e as áreas envolvidas.
 *
 * <p>Só preenche o que o projeto ainda não tem, para não pisar valores escolhidos na conversão manual.
 * Nunca lança exceção por dados da Demanda: corre dentro da aprovação do plano, e uma exceção aqui
 * anularia a aprovação inteira.
 *
 * <p><b>Áreas</b>: as da direção do projeto mantêm o papel (responsável/interveniente) que tinham na
 * Demanda; as das outras direções — participantes ou a direção dona, quando o projeto é de uma direção
 * participante — entram como intervenientes.
 */
@Service
public class DemandProjectInheritanceService {
    private final DemandAreaRepository demandAreas;
    private final DemandParticipatingDirectionRepository participatingDirections;
    private final DemandParticipatingDirectionAreaRepository participatingDirectionAreas;
    private final DemandSuccessCriterionRepository successCriteria;
    private final DemandRequirementsResolver requirements;

    public DemandProjectInheritanceService(DemandAreaRepository demandAreas,
            DemandParticipatingDirectionRepository participatingDirections,
            DemandParticipatingDirectionAreaRepository participatingDirectionAreas,
            DemandSuccessCriterionRepository successCriteria,
            DemandRequirementsResolver requirements) {
        this.demandAreas = demandAreas;
        this.participatingDirections = participatingDirections;
        this.participatingDirectionAreas = participatingDirectionAreas;
        this.successCriteria = successCriteria;
        this.requirements = requirements;
    }

    public void inherit(Project project, Demand demand) {
        if (project.getProfiles().isEmpty()) {
            inheritProfiles(project, demand);
        }
        inheritFields(project, demand);
        if (project.getSuppliers().isEmpty() && demand.getSuppliers() != null) {
            project.getSuppliers().addAll(demand.getSuppliers());
        }
        if (project.getSuccessCriteria().isEmpty()) {
            inheritSuccessCriteria(project, demand);
        }
        if (project.getAreas().isEmpty()) {
            inheritAreas(project, demand);
        }
    }

    private void inheritFields(Project p, Demand d) {
        fill(p::getDescription, p::setDescription, d.getSummary());
        fill(p::getExpectedBenefit, p::setExpectedBenefit, d.getDesiredOutcome());
        fill(p::getExpectedBenefits, p::setExpectedBenefits, d.getDesiredOutcome());
        fill(p::getImpactedSystem, p::setImpactedSystem, d.getImpactedSystem());
        fill(p::getBusinessArea, p::setBusinessArea, truncate(firstNonBlank(d.getAreaName(), d.getAreaCode()), 120));
        fill(p::getResponsibleDirection, p::setResponsibleDirection,
                truncate(firstNonBlank(p.getDirectionName(), d.getDirectionName()), 120));
        fill(p::getBudgetLine, p::setBudgetLine, d.getBudgetLine() == null ? null : d.getBudgetLine().getCode());
        fill(p::getSponsor, p::setSponsor, d.getSponsor());
        fill(p::getRequester, p::setRequester, d.getRequester());
        fill(p::getEasyVistaRef, p::setEasyVistaRef, d.getEasyVistaRef());
        fill(p::getProblemStatement, p::setProblemStatement, d.getProblemStatement());
        fill(p::getCurrentEvidence, p::setCurrentEvidence, d.getCurrentEvidence());
        fill(p::getProposedResponse, p::setProposedResponse, d.getProposedResponse());
        fill(p::getRisksIdentified, p::setRisksIdentified, d.getRisksIdentified());
        fill(p::getDependenciesIdentified, p::setDependenciesIdentified, d.getDependenciesIdentified());
        if (p.getDomain() == null) p.setDomain(d.getDomain());
        if (p.getEstimatedBudget() == null) p.setEstimatedBudget(d.getEstimatedBudget());
        if (p.getPriority() == null) p.setPriority(priorityFromUrgency(d.getUrgency()));
        if (p.getPhaseModel() == null) p.setPhaseModel(d.getType());
        if (p.getDemandNature() == null) p.setDemandNature(d.getNature());
        if (p.getComplexity() == null) p.setComplexity(d.getComplexity());
        if (p.getMandatoryDeadline() == null) p.setMandatoryDeadline(d.getMandatoryDeadline());
        if ((p.getTypeFields() == null || p.getTypeFields().isEmpty()) && d.getTypeFields() != null) {
            p.setTypeFields(new LinkedHashMap<>(d.getTypeFields()));
        }
    }

    private void inheritSuccessCriteria(Project project, Demand demand) {
        List<DemandSuccessCriterion> criteria = successCriteria.findByDemandIdOrderByDisplayOrderAsc(demand.getId());
        int order = 1;
        for (DemandSuccessCriterion criterion : criteria) {
            if (criterion.getDescription() == null || criterion.getDescription().isBlank()) {
                continue;
            }
            ProjectSuccessCriterion copy = new ProjectSuccessCriterion();
            copy.setProject(project);
            copy.setDescription(criterion.getDescription());
            copy.setDisplayOrder(order++);
            project.getSuccessCriteria().add(copy);
        }
    }

    /**
     * Perfis previstos: os mesmos que a Demanda usa na análise de capacidade (derivados do planeamento
     * personalizado ou das fases do Tipo; senão os requisitos gravados). Um por perfil, com o n.º de
     * pessoas em simultâneo (máximo por fase).
     *
     * <p>Quando a Demanda foi dividida num programa com um projeto por direção de implementação, cada
     * projeto recebe os perfis da sua direção; os perfis de outras direções (intervenientes ou sem
     * direção) ficam no projeto da direção principal.
     */
    private void inheritProfiles(Project project, Demand demand) {
        List<DemandProfileRequirement> requirements = this.requirements.forDemand(demand);
        if (requirements.isEmpty()) {
            return;
        }
        List<String> implementationDirections = implementationDirections(demand);
        boolean splitByDirection = project.getProgram() != null && implementationDirections.size() > 1;
        String projectDirection = upper(project.getDirectionCode());
        String leadDirection = implementationDirections.isEmpty() ? null : implementationDirections.get(0);

        Map<UUID, ProfileNeed> needs = new LinkedHashMap<>();
        for (DemandProfileRequirement requirement : requirements) {
            Profile profile = requirement.getProfile();
            if (profile == null || profile.getId() == null) {
                continue;
            }
            if (splitByDirection) {
                String profileDirection = upper(profile.getDirectionCode());
                String owner = profileDirection != null && implementationDirections.contains(profileDirection)
                        ? profileDirection
                        : leadDirection;
                if (!Objects.equals(owner, projectDirection)) {
                    continue;
                }
            }
            int quantity = requirement.getRequiredQuantity() == null ? 1 : Math.max(1, requirement.getRequiredQuantity());
            ProfileNeed need = needs.computeIfAbsent(profile.getId(), id -> new ProfileNeed(profile));
            if (requirement.getPhases() == null || requirement.getPhases().isEmpty()) {
                need.add(null, quantity);
            } else {
                requirement.getPhases().forEach(phase -> need.add(phase, quantity));
            }
        }
        for (ProfileNeed need : needs.values()) {
            ProjectProfile projectProfile = new ProjectProfile();
            projectProfile.setProject(project);
            projectProfile.setProfile(need.profile);
            projectProfile.setProfileName(truncate(need.profile.getName(), 150));
            projectProfile.setDirectionCode(truncate(trim(need.profile.getDirectionCode()), 60));
            projectProfile.setDirectionName(truncate(trim(need.profile.getDirectionName()), 120));
            projectProfile.setRequiredQuantity(need.requiredQuantity());
            projectProfile.setPhaseNames(truncate(need.phaseNames(), 1000));
            project.getProfiles().add(projectProfile);
        }
    }

    /** Mesma ordem que a aprovação do plano usa para criar os projetos de um programa. */
    private List<String> implementationDirections(Demand demand) {
        List<String> codes = new ArrayList<>();
        if (demand.getDirectionParticipationType() == DirectionParticipationType.IMPLEMANTATION
                && hasText(demand.getDirectionCode())) {
            codes.add(upper(demand.getDirectionCode()));
        }
        participatingDirections.findByDemandIdOrderByCreatedAtAsc(demand.getId()).stream()
                .filter(d -> d.getParticipationType() == DirectionParticipationType.IMPLEMANTATION)
                .map(d -> upper(d.getDirectionCode()))
                .filter(code -> code != null && !codes.contains(code))
                .forEach(codes::add);
        return codes;
    }

    /** Pessoas de um perfil por fase; o necessário é o máximo em simultâneo. */
    private static final class ProfileNeed {
        private final Profile profile;
        private final Map<Object, Integer> quantityByPhase = new LinkedHashMap<>();
        private final Map<Object, PhaseDefinition> phases = new LinkedHashMap<>();

        private ProfileNeed(Profile profile) {
            this.profile = profile;
        }

        private void add(PhaseDefinition phase, int quantity) {
            Object key = phase == null ? "*" : phase.getId() != null ? phase.getId() : phase.getName();
            quantityByPhase.merge(key, quantity, Integer::sum);
            if (phase != null) {
                phases.putIfAbsent(key, phase);
            }
        }

        private int requiredQuantity() {
            return quantityByPhase.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        }

        private String phaseNames() {
            String names = phases.values().stream()
                    .sorted(Comparator.comparing(PhaseDefinition::getSequence,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .map(PhaseDefinition::getName)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining("; "));
            return names.isEmpty() ? null : names;
        }
    }

    private void inheritAreas(Project project, Demand demand) {
        String projectDirection = upper(project.getDirectionCode());
        // Chave direção|área: a mesma área só entra uma vez, com o papel mais forte.
        Map<String, ProjectArea> byKey = new LinkedHashMap<>();

        List<DemandArea> ownerAreas = demandAreas.findByDemandIdOrderByCreatedAtAsc(demand.getId());
        if (ownerAreas.isEmpty() && hasText(demand.getAreaCode())) {
            add(byKey, project, projectDirection, demand.getDirectionCode(), demand.getDirectionName(),
                    demand.getAreaCode(), demand.getAreaName(), AreaRole.RESPONSIBLE,
                    demand.getDirectionParticipationType());
        }
        for (DemandArea area : ownerAreas) {
            add(byKey, project, projectDirection, demand.getDirectionCode(), demand.getDirectionName(),
                    area.getAreaCode(), area.getAreaName(), area.getRole(), demand.getDirectionParticipationType());
        }

        for (DemandParticipatingDirection direction : participatingDirections.findByDemandIdOrderByCreatedAtAsc(
                demand.getId())) {
            List<DemandParticipatingDirectionArea> areas =
                    participatingDirectionAreas.findByParticipatingDirectionIdOrderByCreatedAtAsc(direction.getId());
            if (areas.isEmpty()) {
                // Sem áreas detalhadas: usa a área única da direção ou, sem ela, a direção sozinha.
                add(byKey, project, projectDirection, direction.getDirectionCode(), direction.getDirectionName(),
                        direction.getAreaCode(), direction.getAreaName(), AreaRole.RESPONSIBLE,
                        direction.getParticipationType());
            }
            for (DemandParticipatingDirectionArea area : areas) {
                add(byKey, project, projectDirection, direction.getDirectionCode(), direction.getDirectionName(),
                        area.getAreaCode(), area.getAreaName(), area.getRole(), direction.getParticipationType());
            }
        }
        project.getAreas().addAll(byKey.values());
    }

    private void add(Map<String, ProjectArea> byKey, Project project, String projectDirection, String directionCode,
            String directionName, String areaCode, String areaName, AreaRole role,
            DirectionParticipationType participationType) {
        if (!hasText(directionCode) && !hasText(areaCode)) {
            return;
        }
        // Fora da direção do projeto, qualquer área é interveniente.
        AreaRole effectiveRole = projectDirection != null && projectDirection.equals(upper(directionCode))
                ? (role == null ? AreaRole.INTERVENIENT : role)
                : AreaRole.INTERVENIENT;
        String key = upper(directionCode) + "|" + upper(areaCode);
        ProjectArea existing = byKey.get(key);
        if (existing != null) {
            if (effectiveRole == AreaRole.RESPONSIBLE) {
                existing.setRole(AreaRole.RESPONSIBLE);
            }
            return;
        }
        ProjectArea area = new ProjectArea();
        area.setProject(project);
        area.setDirectionCode(truncate(trim(directionCode), 60));
        area.setDirectionName(truncate(trim(directionName), 120));
        area.setAreaCode(truncate(trim(areaCode), 60));
        area.setAreaName(truncate(trim(areaName), 120));
        area.setRole(effectiveRole);
        area.setParticipationType(participationType);
        byKey.put(key, area);
    }

    static Priority priorityFromUrgency(String urgency) {
        if (!hasText(urgency)) {
            return null;
        }
        try {
            return Priority.valueOf(urgency.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void fill(Supplier<String> current, Consumer<String> setter, String value) {
        if (!hasText(current.get()) && hasText(value)) {
            setter.accept(value);
        }
    }

    private static String firstNonBlank(String first, String second) {
        return hasText(first) ? first : hasText(second) ? second : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trim(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static String upper(String value) {
        return hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
