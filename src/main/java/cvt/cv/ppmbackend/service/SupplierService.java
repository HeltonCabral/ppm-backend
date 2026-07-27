package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.SupplierCreateRequest;
import cvt.cv.ppmbackend.entity.Supplier;
import cvt.cv.ppmbackend.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class SupplierService extends AbstractCrudService<Supplier, SupplierCreateRequest> {
    public SupplierService(SupplierRepository r) {
        super(r, "Fornecedor");
    }

    protected Supplier newEntity() {
        return new Supplier();
    }

    protected Supplier apply(SupplierCreateRequest r, Supplier e) {
        validateDates(r.contractStartDate(), r.contractEndDate(), "Data do contrato");
        e.setName(r.name());
        e.setContract(r.contract());
        e.setAvailableHourPackage(r.availableHourPackage());
        e.setConsumedHours(r.consumedHours() == null ? BigDecimal.ZERO : r.consumedHours());
        e.setContractStartDate(r.contractStartDate());
        e.setContractEndDate(r.contractEndDate());
        return e;
    }
}
