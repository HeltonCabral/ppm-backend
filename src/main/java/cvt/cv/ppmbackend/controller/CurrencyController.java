package cvt.cv.ppmbackend.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cvt.cv.ppmbackend.dto.CurrencyCreateRequest;
import cvt.cv.ppmbackend.entity.Currency;
import cvt.cv.ppmbackend.service.CurrencyService;

@RestController
@RequestMapping("/currencies")
public class CurrencyController extends AbstractCrudController<Currency, CurrencyCreateRequest> {

    public CurrencyController(CurrencyService service) {
        super(service);
    }
}
