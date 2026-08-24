package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.SupplierCreateRequest;
import cvt.cv.ppmbackend.entity.Supplier;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.CurrencyRepository;
import cvt.cv.ppmbackend.repository.SupplierRepository;
import org.springframework.stereotype.Service;

@Service
public class SupplierService extends AbstractCrudService<Supplier, SupplierCreateRequest> {
    private final CurrencyRepository currencies;

    public SupplierService(SupplierRepository r, CurrencyRepository currencies) {
        super(r, "Fornecedor");
        this.currencies = currencies;
    }

    protected Supplier newEntity() {
        return new Supplier();
    }

    protected Supplier apply(SupplierCreateRequest r, Supplier e) {
        e.setName(r.name());
        e.setCountry(r.country());
        e.setRatePerHour(r.ratePerHour());
        e.setRateUnit(r.rateUnit());
        
        e.setCurrency(r.currencyId() == null ? null : currencies.findById(r.currencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Moeda não encontrada: " + r.currencyId())));
        return e;
    }
}
