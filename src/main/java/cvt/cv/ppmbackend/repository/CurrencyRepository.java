package cvt.cv.ppmbackend.repository;

import cvt.cv.ppmbackend.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CurrencyRepository extends JpaRepository<Currency, UUID> {
}
