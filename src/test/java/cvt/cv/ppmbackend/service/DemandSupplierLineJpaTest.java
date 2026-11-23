package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandSupplierLineDtos.Input;
import cvt.cv.ppmbackend.dto.DemandSupplierLineDtos.Response;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.Supplier;
import cvt.cv.ppmbackend.enums.RateUnit;
import cvt.cv.ppmbackend.enums.SupplierCostMode;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.DemandSupplierLineRepository;
import cvt.cv.ppmbackend.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** A demand's suppliers keep their hours or value, and the supplier links follow the lines. */
@DataJpaTest
class DemandSupplierLineJpaTest {

    @Autowired private TestEntityManager em;
    @Autowired private DemandRepository demands;
    @Autowired private DemandSupplierLineRepository lines;
    @Autowired private SupplierRepository suppliers;

    private DemandSupplierLineService service;
    private Demand demand;
    private Supplier symplifyr;
    private Supplier accenture;

    @BeforeEach
    void setUp() {
        service = new DemandSupplierLineService(lines, demands, suppliers);
        demand = new Demand();
        demand.setCode("DEM-1");
        demand.setTitle("Demanda");
        em.persist(demand);
        symplifyr = supplier("Symplifyr");
        accenture = supplier("Accenture");
        em.flush();
    }

    @Test
    void hoursAndValueAreKeptAndReadBack() {
        service.replace(demand.getId(), List.of(
                new Input(symplifyr.getId(), SupplierCostMode.HOURS, new BigDecimal("120")),
                new Input(accenture.getId(), SupplierCostMode.VALUE, new BigDecimal("500000"))));
        em.flush();
        em.clear();

        List<Response> read = service.list(demand.getId());

        assertThat(read).extracting(Response::supplierName, Response::mode, (line) -> line.quantity().intValueExact())
                .containsExactly(
                        tuple("Symplifyr", SupplierCostMode.HOURS, 120),
                        tuple("Accenture", SupplierCostMode.VALUE, 500000));
        assertThat(demands.findById(demand.getId()).orElseThrow().getSuppliers()).extracting(Supplier::getName)
                .containsExactlyInAnyOrder("Symplifyr", "Accenture");
    }

    @Test
    void replacingDropsTheSuppliersLeftOut() {
        service.replace(demand.getId(), List.of(
                new Input(symplifyr.getId(), SupplierCostMode.HOURS, BigDecimal.TEN),
                new Input(accenture.getId(), SupplierCostMode.HOURS, BigDecimal.ONE)));
        em.flush();

        service.replace(demand.getId(), List.of(new Input(accenture.getId(), SupplierCostMode.VALUE, null)));
        em.flush();
        em.clear();

        assertThat(service.list(demand.getId())).extracting(Response::supplierName, Response::quantity)
                .containsExactly(tuple("Accenture", null));
        assertThat(demands.findById(demand.getId()).orElseThrow().getSuppliers()).extracting(Supplier::getName)
                .containsExactly("Accenture");
    }

    @Test
    void aSupplierUnlinkedElsewhereIsNotListed() {
        service.replace(demand.getId(), List.of(new Input(symplifyr.getId(), SupplierCostMode.HOURS, BigDecimal.TEN)));
        em.flush();
        Demand stored = demands.findById(demand.getId()).orElseThrow();
        stored.getSuppliers().clear();
        em.flush();
        em.clear();

        assertThat(service.list(demand.getId())).isEmpty();
    }

    @Test
    void repeatedOrUnknownSuppliersAreRefused() {
        assertThatThrownBy(() -> service.replace(demand.getId(), List.of(
                new Input(symplifyr.getId(), SupplierCostMode.HOURS, BigDecimal.TEN),
                new Input(symplifyr.getId(), SupplierCostMode.VALUE, BigDecimal.ONE))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.replace(demand.getId(), List.of(
                new Input(UUID.randomUUID(), SupplierCostMode.HOURS, BigDecimal.TEN))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private Supplier supplier(String name) {
        Supplier supplier = new Supplier();
        supplier.setName(name);
        supplier.setCountry("Portugal");
        supplier.setRatePerHour(new BigDecimal("46.75"));
        supplier.setRateUnit(RateUnit.HOURLY);
        return em.persist(supplier);
    }
}
