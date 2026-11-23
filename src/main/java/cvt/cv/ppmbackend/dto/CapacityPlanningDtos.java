package cvt.cv.ppmbackend.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Capacidade das pessoas e dos perfis nos projetos. Meses em {@code YYYY-MM}; as listas por mês seguem a
 * ordem de {@code months}. Pessoas em % (pico diário do mês); perfis em FTE (proporção dos dias do mês).
 */
public final class CapacityPlanningDtos {
    private CapacityPlanningDtos() {
    }

    // ----- GET /projects/{id}/capacity -----

    public record ProjectCapacityResponse(List<String> months, List<MemberCapacity> members,
            List<ProfileCapacity> profiles) {
    }

    /**
     * @param thisProject alocação neste projeto nos meses em que está ativa (0 nos outros)
     * @param total       pico diário do mês, somando todos os projetos que ocupam pessoas
     * @param breakdown   por mês, cada projeto ativo com a sua percentagem
     * @param assumed     alocação ou período não guardados: usado o período efetivo
     * @param capacity    capacidade individual da pessoa por mês (o dia mais baixo do mês); sobrealocação = total > capacity
     * @param capacityReason motivo da capacidade em vigor hoje (nulo a 100%)
     */
    public record MemberCapacity(UUID memberId, String name, String userName, String profileName,
            UUID projectProfileId, int allocationPercentage, LocalDate startDate, LocalDate endDate, boolean assumed,
            List<Integer> thisProject, List<Integer> total, List<List<ProjectShare>> breakdown, int peak,
            List<Integer> capacity, String capacityReason, List<Integer> concurrent, int maxConcurrent) {
    }

    /** Projeto sem permissão de ver: {@code projectId}/{@code projectCode} nulos e nome "Outro projeto". */
    public record ProjectShare(UUID projectId, String projectCode, String projectName, int percent,
            boolean thisProject) {
    }

    public record ProfileCapacity(UUID projectProfileId, UUID profileId, String profileName, String directionCode,
            List<Double> required, List<Double> assigned, List<Double> portfolioLoad, Integer available) {
    }

    // ----- GET /capacity/people/{username} -----

    public record PersonLoadResponse(List<String> months, List<Integer> load, List<PersonAssignment> assignments,
            List<Integer> capacity, String capacityReason, List<Integer> concurrent, int maxConcurrent) {
    }

    public record PersonAssignment(UUID projectId, String projectCode, String projectName, int allocationPercentage,
            LocalDate startDate, LocalDate endDate, boolean assumed) {
    }

    // ----- Portefólio: GET /capacity/profiles e /capacity/people -----

    public record PortfolioProfilesResponse(List<String> months, List<PortfolioProfile> profiles) {
    }

    /** {@code load}: o que os projetos abertos pedem; {@code assigned}: o que está alocado; ambos em FTE. */
    public record PortfolioProfile(UUID profileId, String profileName, String directionCode, Integer available,
            List<Double> load, List<Double> assigned, List<List<ProjectProfileShare>> projects) {
    }

    public record ProjectProfileShare(UUID projectId, String projectCode, String projectName, double required) {
    }

    public record PortfolioPeopleResponse(List<String> months, List<PortfolioPerson> people) {
    }

    /** {@code capacity}: capacidade individual por mês (100 sem registo); folga = capacity - load. */
    public record PortfolioPerson(String userName, String name, String directionCode, List<Integer> load, int peak,
            List<Integer> capacity, List<Integer> concurrent, int maxConcurrent) {
    }
}
