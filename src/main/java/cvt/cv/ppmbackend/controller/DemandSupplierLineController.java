package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.DemandSupplierLineDtos.Input;
import cvt.cv.ppmbackend.dto.DemandSupplierLineDtos.Response;
import cvt.cv.ppmbackend.service.DemandAccessService;
import cvt.cv.ppmbackend.service.DemandSupplierLineService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The suppliers of a demand with their hours or value; PUT replaces them all. */
@RestController
@RequestMapping("/demands/{demandId}/supplier-lines")
public class DemandSupplierLineController {
    private final DemandSupplierLineService lines;
    private final DemandAccessService access;

    public DemandSupplierLineController(DemandSupplierLineService lines, DemandAccessService access) {
        this.lines = lines;
        this.access = access;
    }

    @GetMapping
    public List<Response> list(@PathVariable UUID demandId) {
        access.requireView(demandId);
        return lines.list(demandId);
    }

    @PutMapping
    public List<Response> replace(@PathVariable UUID demandId, @Valid @RequestBody List<@Valid Input> inputs) {
        access.requireEdit(demandId);
        return lines.replace(demandId, inputs);
    }
}
