package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.CapacityPlanningDtos.PersonLoadResponse;
import cvt.cv.ppmbackend.dto.CapacityPlanningDtos.PortfolioPeopleResponse;
import cvt.cv.ppmbackend.dto.CapacityPlanningDtos.PortfolioProfilesResponse;
import cvt.cv.ppmbackend.dto.CapacityPlanningDtos.ProjectCapacityResponse;
import cvt.cv.ppmbackend.service.ProjectCapacityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Capacidade das pessoas e dos perfis: no projeto, por pessoa e no portefólio. */
@RestController
public class CapacityPlanningController {
    private final ProjectCapacityService capacity;

    public CapacityPlanningController(ProjectCapacityService capacity) {
        this.capacity = capacity;
    }

    /** Mês a mês entre o início e o fim previstos do projeto (ou {@code from}/{@code to}). */
    @GetMapping("/projects/{id}/capacity")
    public ProjectCapacityResponse project(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return capacity.projectCapacity(id, from, to);
    }

    /** Carga de uma pessoa noutros projetos (diálogo "Gerir equipa"). */
    @GetMapping("/capacity/people/{username}")
    public PersonLoadResponse person(@PathVariable String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID excludeProjectId) {
        return capacity.person(username, from, to, excludeProjectId);
    }

    /** Página /capacity: por perfil de capacidade e por mês. */
    @GetMapping("/capacity/profiles")
    public PortfolioProfilesResponse profiles(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return capacity.portfolioProfiles(from, to);
    }

    /** Página /capacity: por pessoa e por mês, para encontrar quem tem folga. */
    @GetMapping("/capacity/people")
    public PortfolioPeopleResponse people(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String directionCode) {
        return capacity.portfolioPeople(from, to, directionCode);
    }
}
