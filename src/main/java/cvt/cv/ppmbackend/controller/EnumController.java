package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.entity.LookupValue;
import cvt.cv.ppmbackend.enums.ProjectDomain;
import cvt.cv.ppmbackend.enums.ProjectPhase;
import cvt.cv.ppmbackend.enums.ProjectType;
import cvt.cv.ppmbackend.service.LookupValueService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/enums")
public class EnumController {
    private final LookupValueService lookupService;

    public EnumController(LookupValueService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping
    public Map<String, List<String>> getAll() {
        Map<String, List<String>> enums = new LinkedHashMap<>();
        // Fixed enums
        enums.put("projectTypes", toList(ProjectType.values()));
        enums.put("projectPhases", toList(ProjectPhase.values()));
        enums.put("projectDomains", toList(ProjectDomain.values()));
        // Dynamic lookups
        List<LookupValue> all = lookupService.findAll();
        all.stream().collect(Collectors.groupingBy(LookupValue::getCategory)).forEach((category, values) -> {
            enums.put(category, values.stream().map(LookupValue::getCode).toList());
        });
        return enums;
    }

    @GetMapping("/project-types")
    public List<String> getProjectTypes() {
        return toList(ProjectType.values());
    }

    @GetMapping("/project-phases")
    public List<String> getProjectPhases() {
        return toList(ProjectPhase.values());
    }

    @GetMapping("/project-domains")
    public List<String> getProjectDomains() {
        return toList(ProjectDomain.values());
    }

    private <E extends Enum<E>> List<String> toList(E[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
