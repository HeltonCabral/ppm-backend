package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.AccessControlProperties;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandPermissions;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Regra de acesso às demandas por direção, calculada em cada pedido a partir da direção atual da
 * demanda (se a direção responsável mudar, o acesso acompanha logo).
 *
 * <p><b>Ver</b>: {@code demanda_ver_todas}/{@code demanda_editar_todas}; ou criou a demanda; ou tem
 * {@code demanda_ver_direcao} ou {@code demanda_editar_direcao} e a direção responsável ou uma
 * participante (Implementação ou Interveniente) é uma das suas.
 *
 * <p><b>Editar</b>: {@code demanda_editar_todas}; ou {@code demanda_editar_direcao} e a direção
 * <i>responsável</i> é uma das suas; ou criou a demanda e esta ainda está em rascunho.
 *
 * <p>Com {@code ppm.access.direction-filter.enabled=false} nada é filtrado nem recusado, e
 * {@code canEdit} reflete só o estado.
 */
@Service
public class DemandAccessService {
    public static final String EDIT_FORBIDDEN_MESSAGE = "Não tem permissão para editar esta demanda.";

    /** Estados do ciclo em que a demanda já não se edita: submetida ao comité ou no plano. */
    private static final Set<DemandPlanningStatus> LOCKED_STATUSES =
            EnumSet.of(DemandPlanningStatus.PRIORITIZED, DemandPlanningStatus.IN_PLAN);

    private final AccessControlProperties properties;
    private final UserAccessService userAccess;
    private final DemandRepository demands;
    private final DemandParticipatingDirectionRepository participatingDirections;
    private final DemandPlanningCycleService planningCycles;

    public DemandAccessService(AccessControlProperties properties, UserAccessService userAccess,
            DemandRepository demands, DemandParticipatingDirectionRepository participatingDirections,
            DemandPlanningCycleService planningCycles) {
        this.properties = properties;
        this.userAccess = userAccess;
        this.demands = demands;
        this.participatingDirections = participatingDirections;
        this.planningCycles = planningCycles;
    }

    public boolean filterEnabled() {
        return properties.directionFilterEnabled();
    }

    /** Predicado "Ver" para acrescentar à pesquisa de demandas. */
    public Specification<Demand> visibleTo(UserAccess access) {
        return (root, query, cb) -> {
            if (access.viewAll()) {
                return cb.conjunction();
            }
            List<Predicate> anyOf = new ArrayList<>();
            anyOf.add(cb.equal(cb.lower(root.get("createdBy")), access.userName()));
            Set<String> codes = access.directionCodes();
            if (access.viewsDirection() && !codes.isEmpty()) {
                anyOf.add(involvesDirections(root, query, cb, codes));
            }
            return cb.or(anyOf.toArray(Predicate[]::new));
        };
    }

    /**
     * A demanda é de uma destas direções (códigos em maiúsculas): responsável, ou participante como
     * Implementação ou Interveniente. Serve ao acesso e ao filtro por direção das listas.
     */
    public static Predicate involvesDirections(Path<Demand> demand, CriteriaQuery<?> query, CriteriaBuilder cb,
            Collection<String> codes) {
        return cb.or(cb.upper(demand.get("directionCode")).in(codes), participatesIn(demand, query, cb, codes));
    }

    /**
     * Uma destas direções (códigos em maiúsculas) participa na demanda, como Implementação ou
     * Interveniente. A direção responsável só conta se também estiver listada como participante.
     */
    public static Predicate participatesIn(Path<Demand> demand, CriteriaQuery<?> query, CriteriaBuilder cb,
            Collection<String> codes) {
        Subquery<UUID> participating = query.subquery(UUID.class);
        Root<DemandParticipatingDirection> pd = participating.from(DemandParticipatingDirection.class);
        participating.select(pd.get("id")).where(
                cb.equal(pd.get("demand"), demand),
                cb.upper(pd.get("directionCode")).in(codes));
        return cb.exists(participating);
    }

    public Specification<Demand> visibleToCurrentUser() {
        return visibleTo(userAccess.current());
    }

    public boolean canView(Demand demand, Collection<String> participatingDirectionCodes, UserAccess access) {
        if (access.viewAll() || access.isCreator(demand.getCreatedBy())) {
            return true;
        }
        if (!access.viewsDirection()) {
            return false;
        }
        return access.belongsTo(demand.getDirectionCode())
                || participatingDirectionCodes.stream().anyMatch(access::belongsTo);
    }

    /** Só as permissões; o estado da demanda fica de fora (ver {@link #stateAllowsEdit}). */
    public boolean hasEditPermission(Demand demand, Optional<DemandPlanningCycle> currentCycle, UserAccess access) {
        if (access.editAll()) {
            return true;
        }
        if (access.has(UserAccess.EDIT_DIRECTION) && access.belongsTo(demand.getDirectionCode())) {
            return true;
        }
        return access.isCreator(demand.getCreatedBy())
                && currentCycle.map(DemandPlanningCycle::getStatus).orElse(null) == DemandPlanningStatus.DRAFT;
    }

    /** Estados que já bloqueavam a edição: submetida ao comité, em deliberação, no plano, convertida. */
    public boolean stateAllowsEdit(Optional<DemandPlanningCycle> currentCycle) {
        return currentCycle.map(cycle -> !LOCKED_STATUSES.contains(cycle.getStatus())
                && !cycle.isInStrategicCommittee()
                && cycle.getConvertedProject() == null
                && cycle.getConvertedProgram() == null)
                .orElse(true);
    }

    /** Campo {@code permissions} das respostas de demandas. */
    public DemandPermissions permissionsFor(Demand demand, Collection<String> participatingDirectionCodes,
            Optional<DemandPlanningCycle> currentCycle) {
        boolean stateAllows = stateAllowsEdit(currentCycle);
        if (!filterEnabled()) {
            return new DemandPermissions(true, stateAllows);
        }
        UserAccess access = userAccess.current();
        boolean canView = canView(demand, participatingDirectionCodes, access);
        boolean canEdit = canView && stateAllows && hasEditPermission(demand, currentCycle, access);
        return new DemandPermissions(canView, canEdit);
    }

    /** 404 quando o utilizador não pode ver a demanda, para não revelar que existe. */
    public void requireView(UUID demandId) {
        if (!filterEnabled()) {
            return;
        }
        Demand demand = load(demandId);
        if (!canView(demand, participatingDirectionCodes(demandId), userAccess.current())) {
            throw notFound(demandId);
        }
    }

    /**
     * 404 se não pode ver, 403 se pode ver mas não editar. Só verifica permissões: o bloqueio por estado
     * continua a cargo das regras de cada operação, como antes.
     */
    public void requireEdit(UUID demandId) {
        if (!filterEnabled()) {
            return;
        }
        Demand demand = load(demandId);
        UserAccess access = userAccess.current();
        if (!canView(demand, participatingDirectionCodes(demandId), access)) {
            throw notFound(demandId);
        }
        if (!hasEditPermission(demand, planningCycles.getCurrentPlanningCycle(demandId), access)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "DEMAND_EDIT_FORBIDDEN", EDIT_FORBIDDEN_MESSAGE,
                    Map.of("id", demandId.toString()));
        }
    }

    private Demand load(UUID demandId) {
        return demands.findByIdAndDeletedAtIsNull(demandId).orElseThrow(() -> notFound(demandId));
    }

    private List<String> participatingDirectionCodes(UUID demandId) {
        return participatingDirections.findByDemandIdOrderByCreatedAtAsc(demandId).stream()
                .map(DemandParticipatingDirection::getDirectionCode)
                .toList();
    }

    private DomainException notFound(UUID demandId) {
        return new DomainException(HttpStatus.NOT_FOUND, "DEMAND_NOT_FOUND", "Demanda não encontrada",
                Map.of("id", demandId.toString()));
    }
}
