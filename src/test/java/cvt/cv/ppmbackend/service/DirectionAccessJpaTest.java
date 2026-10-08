package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.AccessControlProperties;
import cvt.cv.ppmbackend.dto.AccessDtos.BulkResult;
import cvt.cv.ppmbackend.dto.AccessDtos.DirectionRef;
import cvt.cv.ppmbackend.dto.AccessDtos.UserDirectionInput;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandParticipatingDirection;
import cvt.cv.ppmbackend.entity.UserDirection;
import cvt.cv.ppmbackend.enums.DirectionParticipationType;
import cvt.cv.ppmbackend.enums.UserDirectionSource;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.UserDirectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class DirectionAccessJpaTest {

    @Autowired private TestEntityManager em;
    @Autowired private DemandRepository demandRepository;
    @Autowired private UserDirectionRepository userDirectionRepository;

    private DemandAccessService access;
    private UserDirectionService userDirections;
    private UserAccessService userAccess;

    @BeforeEach
    void setUp() {
        AccessControlProperties properties = new AccessControlProperties();
        properties.getDirectionFilter().setEnabled(true);
        userAccess = new UserAccessService(userDirectionRepository, properties);
        access = new DemandAccessService(properties, userAccess, demandRepository, null, null);
        userDirections = new UserDirectionService(userDirectionRepository);

        persistDemand("D-DSI", "DSI", "outro");
        persistDemand("D-DOM", "DOM", "outro");
        persistDemand("D-MINE", "DOM", "CVT00983");
        Demand domWithDsi = persistDemand("D-DOM-DSI", "DOM", "outro");
        DemandParticipatingDirection participating = new DemandParticipatingDirection();
        participating.setDemand(domWithDsi);
        participating.setDirectionCode("dsi");
        participating.setParticipationType(DirectionParticipationType.IMPLEMANTATION);
        em.persist(participating);
        em.flush();
    }

    // 2. Lista: vê as DSI (responsável ou participante) e as que criou; não vê as DOM.
    @Test
    void listShowsOwnDirectionParticipatingAndCreatedDemands() {
        assertThat(visibleCodes(user("cvt00983", Set.of(UserAccess.VIEW_DIRECTION), "DSI")))
                .containsExactlyInAnyOrder("D-DSI", "D-MINE", "D-DOM-DSI");
    }

    // 5. Sem direção: só as que criou.
    @Test
    void listWithoutDirectionShowsOnlyCreatedDemands() {
        assertThat(visibleCodes(user("cvt00983", Set.of(UserAccess.VIEW_DIRECTION))))
                .containsExactly("D-MINE");
        assertThat(visibleCodes(user("cvt00983", Set.of(), "DSI"))).containsExactly("D-MINE");
    }

    // 7. demanda_ver_todas vê tudo.
    @Test
    void listWithViewAllShowsEverything() {
        assertThat(visibleCodes(user("comite", Set.of(UserAccess.VIEW_ALL)))).hasSize(4);
    }

    // 6. Atribuição com valid_until ontem deixa de contar.
    @Test
    void expiredAssignmentNoLongerCounts() {
        persistAssignment("cvt00983", "DSI", LocalDate.now().minusDays(1));
        persistAssignment("cvt00983", "DOM", LocalDate.now());
        persistAssignment("cvt00983", "DCF", null);

        UserAccess resolved = userAccess.resolve("CVT00983", "demanda_ver_direcao");

        assertThat(resolved.userName()).isEqualTo("cvt00983");
        assertThat(resolved.directions()).extracting(DirectionRef::code).containsExactly("DCF", "DOM");
    }

    // 9. Bulk com um par repetido: skipped, sem erro.
    @Test
    void bulkSkipsExistingAndRepeatedPairs() {
        persistAssignment("cvt00983", "DSI", null);

        BulkResult result = userDirections.bulk(List.of(
                input("CVT00983", "dsi", null),
                input("cvt00984", "DSI", null),
                input("cvt00984", "DSI", null),
                input("", "DSI", null),
                input("cvt00985", "DOM", LocalDate.now().minusDays(1))), "admin");

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.errors()).containsExactly(
                "Linha 4: userName é obrigatório",
                "Linha 5: validUntil não pode ser anterior a hoje");
    }

    @Test
    void createRejectsDuplicatePairWithConflict() {
        userDirections.create(input("cvt00983", "DSI", null), "admin");

        assertThatThrownBy(() -> userDirections.create(input("CVT00983", " dsi ", null), "admin"))
                .isInstanceOfSatisfying(DomainException.class,
                        e -> assertThat(e.getStatus().value()).isEqualTo(409));
        assertThatThrownBy(() -> userDirections.create(input("cvt00983", "DOM",
                LocalDate.now().minusDays(1)), "admin"))
                .isInstanceOf(BadRequestException.class);
    }

    private List<String> visibleCodes(UserAccess user) {
        Specification<Demand> spec = (root, query, cb) -> cb.isNull(root.get("deletedAt"));
        return demandRepository.findAll(spec.and(access.visibleTo(user))).stream().map(Demand::getCode).toList();
    }

    private static UserAccess user(String userName, Set<String> permissions, String... directions) {
        return new UserAccess(userName, permissions,
                Arrays.stream(directions).map(code -> new DirectionRef(code, null)).toList());
    }

    private static UserDirectionInput input(String userName, String directionCode, LocalDate validUntil) {
        return new UserDirectionInput(userName, null, directionCode, null, validUntil, UserDirectionSource.AD, null);
    }

    private Demand persistDemand(String code, String directionCode, String createdBy) {
        Demand demand = new Demand();
        demand.setCode(code);
        demand.setTitle(code);
        demand.setProblemStatement("problema");
        demand.setDesiredOutcome("resultado");
        demand.setDirectionCode(directionCode);
        demand.setCreatedBy(createdBy);
        return em.persist(demand);
    }

    private void persistAssignment(String userName, String directionCode, LocalDate validUntil) {
        UserDirection assignment = new UserDirection();
        assignment.setUserName(userName);
        assignment.setDirectionCode(directionCode);
        assignment.setValidUntil(validUntil);
        em.persistAndFlush(assignment);
    }
}
