package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.SupplierCreateRequest;
import cvt.cv.ppmbackend.entity.Supplier;
import cvt.cv.ppmbackend.service.SupplierService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/suppliers")
public class SupplierController extends AbstractCrudController<Supplier, SupplierCreateRequest> {
    public SupplierController(SupplierService s) {
        super(s);
    }
}
