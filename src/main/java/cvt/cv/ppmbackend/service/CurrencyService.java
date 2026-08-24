package cvt.cv.ppmbackend.service;

import org.springframework.stereotype.Service;

import cvt.cv.ppmbackend.dto.CurrencyCreateRequest;
import cvt.cv.ppmbackend.entity.Currency;
import cvt.cv.ppmbackend.repository.CurrencyRepository;

@Service
public class CurrencyService extends AbstractCrudService<Currency, CurrencyCreateRequest> {
    public CurrencyService(CurrencyRepository repository) {
        super(repository, "Moeda");
    }

    @Override
    protected Currency newEntity() {
        return new Currency();
    }

    @Override
    protected Currency apply(CurrencyCreateRequest request, Currency entity) {
        
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setSymbol(request.symbol());
        entity.setRateToEcv(request.rateToEcv());
        return entity;
    }
}
