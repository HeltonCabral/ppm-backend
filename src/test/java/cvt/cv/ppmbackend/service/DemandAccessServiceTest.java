package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.AccessControlProperties;
import cvt.cv.ppmbackend.dto.AccessDtos.DirectionRef;
import cvt.cv.ppmbackend.dto.DemandDtos.DemandPermissions;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.DemandPlanningCycle;
import cvt.cv.ppmbackend.enums.DemandPlanningStatus;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.DemandParticipatingDirectionRepository;
import cvt.cv.ppmbackend.repository.DemandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemandAccessServiceTest {

    @Mock private UserAccessService userAccess;
    @Mock private DemandRepository demands;
    @Mock private DemandParticipatingDirectionRepository participatingDirections;
    @Mock private DemandPlanningCycleService planningCycles;

    private final AccessControlProperties properties = new AccessControlProperties();
    private DemandAccessService service;

    private final UUID dsiDemandId = UUID.randomUUID();
    private final UUID domDemandId = UUID.randomUUID();
    private Demand dsiDemand;
    private Demand domDemand;

    @BeforeEach
    void setUp() {
        properties.getDirectionFilter().setEnabled(true);
        service = new DemandAccessService(properties, userAccess, demands, participatingDirections, planningCycles);

        dsiDemand = demand(dsiDemandId, "DSI", "outro");
        domDemand = demand(domDemandId, "DOM", "outro");
        lenient().when(demands.findByIdAndDeletedAtIsNull(dsiDemandId)).thenReturn(Optional.of(dsiDemand));
        lenient().when(demands.findByIdAndDeletedAtIsNull(domDemandId)).thenReturn(Optional.of(domDemand));
        lenient().when(participatingDirections.findByDemandIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        lenient().when(planningCycles.getCurrentPlanningCycle(any()))
                .thenReturn(Optional.of(cycle(DemandPlanningStatus.IN_ANALYSIS)));
    }

    // 1. Flag desligada: nada é filtrado nem recusado; canEdit só pelo estado.
    @Test
    void flagOffChangesNothing() {
        properties.getDirectionFilter().setEnabled(false);

        assertThatCode(() -> service.requireView(domDemandId)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireEdit(domDemandId)).doesNotThrowAnyException();
        assertThat(service.permissionsFor(domDemand, List.of(), Optional.of(cycle(DemandPlanningStatus.DRAFT))))
                .isEqualTo(new DemandPermissions(true, true));
        assertThat(service.permissionsFor(domDemand, List.of(), Optional.of(cycle(DemandPlanningStatus.IN_PLAN))))
                .isEqualTo(new DemandPermissions(true, false));
        verifyNoInteractions(userAccess, demands);
    }

    // 2. Ver por direção: vê as DSI e as que criou; não vê as DOM (404).
    @Test
    void viewDirectionSeesOwnDirectionAndOwnDemandsOnly() {
        as("cvt00983", Set.of(UserAccess.VIEW_DIRECTION), "DSI");
        Demand createdByMe = demand(UUID.randomUUID(), "DOM", "CVT00983");

        assertThat(service.canView(dsiDemand, List.of(), userAccess.current())).isTrue();
        assertThat(service.canView(createdByMe, List.of(), userAccess.current())).isTrue();
        assertThatCode(() -> service.requireView(dsiDemandId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.requireView(domDemandId))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void participatingDirectionGrantsViewButNotEdit() {
        as("cvt00983", Set.of(UserAccess.VIEW_DIRECTION, UserAccess.EDIT_DIRECTION), "DSI");
        DemandParticipatingDirection dsi = new DemandParticipatingDirection();
        dsi.setDirectionCode("dsi");
        when(participatingDirections.findByDemandIdOrderByCreatedAtAsc(domDemandId)).thenReturn(List.of(dsi));

        assertThatCode(() -> service.requireView(domDemandId)).doesNotThrowAnyException();
        // 4. ...numa demanda DOM em que a DSI só participa → 403.
        assertForbidden(domDemandId);
        assertThat(service.permissionsFor(domDemand, List.of("DSI"), Optional.of(cycle(DemandPlanningStatus.DRAFT))))
                .isEqualTo(new DemandPermissions(true, false));
    }

    // 3. DSI sem demanda_editar_direcao: PUT numa demanda DSI → 403; canEdit false.
    @Test
    void withoutEditDirectionCannotEdit() {
        as("cvt00983", Set.of(UserAccess.VIEW_DIRECTION), "DSI");

        assertForbidden(dsiDemandId);
        assertThat(service.permissionsFor(dsiDemand, List.of(), Optional.of(cycle(DemandPlanningStatus.DRAFT))))
                .isEqualTo(new DemandPermissions(true, false));
    }

    // 4. DSI com demanda_editar_direcao: PUT numa demanda DSI → permitido.
    @Test
    void editDirectionEditsDemandsOfResponsibleDirection() {
        as("cvt00983", Set.of(UserAccess.VIEW_DIRECTION, UserAccess.EDIT_DIRECTION), "DSI");

        assertThatCode(() -> service.requireEdit(dsiDemandId)).doesNotThrowAnyException();
        assertThat(service.permissionsFor(dsiDemand, List.of(),
                Optional.of(cycle(DemandPlanningStatus.IN_ANALYSIS))))
                .isEqualTo(new DemandPermissions(true, true));
    }

    @Test
    void lockedStatesBlockCanEditEvenWithPermission() {
        as("pmo", Set.of(UserAccess.EDIT_ALL));
        DemandPlanningCycle inCommittee = cycle(DemandPlanningStatus.IN_PRIORITIZATION);
        inCommittee.setInStrategicCommittee(true);

        assertThat(service.permissionsFor(dsiDemand, List.of(), Optional.of(cycle(DemandPlanningStatus.PRIORITIZED)))
                .canEdit()).isFalse();
        assertThat(service.permissionsFor(dsiDemand, List.of(), Optional.of(cycle(DemandPlanningStatus.IN_PLAN)))
                .canEdit()).isFalse();
        assertThat(service.permissionsFor(dsiDemand, List.of(), Optional.of(inCommittee)).canEdit()).isFalse();
        assertThat(service.permissionsFor(dsiDemand, List.of(), Optional.of(cycle(DemandPlanningStatus.BACKLOG)))
                .canEdit()).isTrue();
    }

    // 5. Sem direção: só vê e edita (em rascunho) as que criou.
    @Test
    void userWithoutDirectionOnlySeesOwnDemands() {
        as("cvt00983", Set.of(UserAccess.VIEW_DIRECTION, UserAccess.EDIT_DIRECTION));
        Demand mine = demand(UUID.randomUUID(), "DSI", "cvt00983");

        assertThatThrownBy(() -> service.requireView(dsiDemandId)).isInstanceOf(DomainException.class);
        assertThat(service.permissionsFor(mine, List.of(), Optional.of(cycle(DemandPlanningStatus.DRAFT))))
                .isEqualTo(new DemandPermissions(true, true));
        assertThat(service.permissionsFor(mine, List.of(), Optional.of(cycle(DemandPlanningStatus.IN_ANALYSIS))))
                .isEqualTo(new DemandPermissions(true, false));
    }

    // 7. demanda_ver_todas: vê tudo, mas só edita com demanda_editar_todas.
    @Test
    void viewAllSeesEverythingButEditsOnlyWithEditAll() {
        as("comite", Set.of(UserAccess.VIEW_ALL));
        assertThatCode(() -> service.requireView(domDemandId)).doesNotThrowAnyException();
        assertForbidden(domDemandId);

        as("pmo", Set.of(UserAccess.VIEW_ALL, UserAccess.EDIT_ALL));
        assertThatCode(() -> service.requireEdit(domDemandId)).doesNotThrowAnyException();
    }

    // 8. Mudar a direção responsável: o acesso acompanha logo.
    @Test
    void accessFollowsResponsibleDirectionChange() {
        as("cvt00983", Set.of(UserAccess.VIEW_DIRECTION, UserAccess.EDIT_DIRECTION), "DSI");
        assertThatCode(() -> service.requireEdit(dsiDemandId)).doesNotThrowAnyException();

        dsiDemand.setDirectionCode("DOM");

        assertThatThrownBy(() -> service.requireView(dsiDemandId)).isInstanceOf(DomainException.class);
    }

    @Test
    void permissionsAreCaseInsensitive() {
        when(userAccess.current()).thenReturn(new UserAccess("cvt00983",
                UserAccessService.parsePermissions(" Demanda_Ver_Direcao , DEMANDA_EDITAR_DIRECAO "),
                List.of(new DirectionRef("dsi", null))));

        assertThatCode(() -> service.requireEdit(dsiDemandId)).doesNotThrowAnyException();
    }

    private void assertForbidden(UUID demandId) {
        assertThatThrownBy(() -> service.requireEdit(demandId))
                .isInstanceOfSatisfying(DomainException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).isEqualTo(DemandAccessService.EDIT_FORBIDDEN_MESSAGE);
                });
    }

    private void as(String userName, Set<String> permissions, String... directions) {
        lenient().when(userAccess.current()).thenReturn(new UserAccess(userName, permissions,
                Arrays.stream(directions).map(code -> new DirectionRef(code, null)).toList()));
    }

    private static Demand demand(UUID id, String directionCode, String createdBy) {
        Demand demand = new Demand();
        demand.setId(id);
        demand.setDirectionCode(directionCode);
        demand.setCreatedBy(createdBy);
        return demand;
    }

    private static DemandPlanningCycle cycle(DemandPlanningStatus status) {
        DemandPlanningCycle cycle = new DemandPlanningCycle();
        cycle.setStatus(status);
        return cycle;
    }
}
