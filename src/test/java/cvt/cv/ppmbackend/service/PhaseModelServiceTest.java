package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.PhaseModelDtos.ModelRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.PhaseProfileRequest;
import cvt.cv.ppmbackend.dto.PhaseModelDtos.PhaseProfileResponse;
import cvt.cv.ppmbackend.entity.PhaseDefinition;
import cvt.cv.ppmbackend.entity.PhaseModel;
import cvt.cv.ppmbackend.entity.PhaseModelDirection;
import cvt.cv.ppmbackend.entity.PhaseProfile;
import cvt.cv.ppmbackend.entity.Profile;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.PhaseDefinitionRepository;
import cvt.cv.ppmbackend.repository.PhaseModelRepository;
import cvt.cv.ppmbackend.repository.PhaseProfileRepository;
import cvt.cv.ppmbackend.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhaseModelServiceTest {

    @Mock
    private PhaseModelRepository models;
    @Mock
    private PhaseDefinitionRepository phases;
    @Mock
    private PhaseProfileRepository phaseProfiles;
    @Mock
    private ProfileRepository profiles;

    private PhaseModelService service;

    private PhaseModel model;
    private PhaseDefinition analise;

    @BeforeEach
    void setUp() {
        service = new PhaseModelService(models, phases, phaseProfiles, profiles);
        model = new PhaseModel();
        model.setId(UUID.randomUUID());
        model.setCode("PROJ_TI");
        model.setName("Projeto TI");
        model.getApplicableDirections().add(direction(model, "DSI"));
        analise = new PhaseDefinition();
        analise.setId(UUID.randomUUID());
        analise.setPhaseModel(model);
        analise.setCode("ANALISE");
        analise.setName("Análise");
        analise.setSequence(1);
        analise.setDurationWeightPercentage(new BigDecimal("100.00"));
        analise.setActive(true);
        model.getPhases().add(analise);

        lenient().when(models.findWithPhasesById(model.getId())).thenReturn(Optional.of(model));
    }

    @Test
    void addsPhaseProfileWithAllocationForApplicableDirection() {
        Profile admin = profile("Administrador de Sistemas", "DSI", true);
        when(profiles.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(phaseProfiles.existsByPhaseDefinitionIdAndProfile_Id(analise.getId(), admin.getId())).thenReturn(false);
        when(phaseProfiles.saveAndFlush(any(PhaseProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        PhaseProfileResponse response = service.addPhaseProfile(model.getId(), analise.getId(),
                new PhaseProfileRequest(admin.getId(), new BigDecimal("150")));

        assertThat(response.allocationPercentage()).isEqualByComparingTo("150.00");
        assertThat(response.profileName()).isEqualTo("Administrador de Sistemas");
        assertThat(response.directionCode()).isEqualTo("DSI");
    }

    @Test
    void rejectsProfileFromNonApplicableDirection() {
        Profile other = profile("Analista Financeiro", "DFI", true);
        when(profiles.findById(other.getId())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.addPhaseProfile(model.getId(), analise.getId(),
                new PhaseProfileRequest(other.getId(), new BigDecimal("100"))))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(de.getCode()).isEqualTo("PROFILE_DIRECTION_NOT_APPLICABLE");
                });
    }

    @Test
    void rejectsSameProfileTwiceInSamePhase() {
        Profile admin = profile("Administrador de Sistemas", "DSI", true);
        when(profiles.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(phaseProfiles.existsByPhaseDefinitionIdAndProfile_Id(analise.getId(), admin.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.addPhaseProfile(model.getId(), analise.getId(),
                new PhaseProfileRequest(admin.getId(), new BigDecimal("100"))))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(de.getCode()).isEqualTo("PHASE_PROFILE_CONFLICT");
                });
    }

    @Test
    void rejectsRemovingApplicableDirectionWithAssociatedProfiles() {
        Profile admin = profile("Administrador de Sistemas", "DSI", true);
        PhaseProfile phaseProfile = new PhaseProfile();
        phaseProfile.setId(UUID.randomUUID());
        phaseProfile.setPhaseDefinition(analise);
        phaseProfile.setProfile(admin);
        phaseProfile.setAllocationPercentage(new BigDecimal("150.00"));
        when(models.existsByCodeIgnoreCaseAndIdNot("PROJ_TI", model.getId())).thenReturn(false);
        when(phaseProfiles.findByPhaseModelId(model.getId())).thenReturn(List.of(phaseProfile));

        ModelRequest request = new ModelRequest("PROJ_TI", "Projeto TI", null, List.of(), null, null);

        assertThatThrownBy(() -> service.update(model.getId(), request))
                .isInstanceOf(DomainException.class)
                .satisfies(ex -> {
                    DomainException de = (DomainException) ex;
                    assertThat(de.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(de.getCode()).isEqualTo("DIRECTION_HAS_PHASE_PROFILES");
                });
    }

    private PhaseModelDirection direction(PhaseModel model, String code) {
        PhaseModelDirection direction = new PhaseModelDirection();
        direction.setId(UUID.randomUUID());
        direction.setPhaseModel(model);
        direction.setDirectionCode(code);
        direction.setDirectionName(code);
        return direction;
    }

    private Profile profile(String name, String directionCode, boolean active) {
        Profile profile = new Profile();
        profile.setId(UUID.randomUUID());
        profile.setName(name);
        profile.setDirectionCode(directionCode);
        profile.setDirectionName(directionCode);
        profile.setActive(active);
        return profile;
    }
}
