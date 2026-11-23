package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.DemandSupplierLineDtos.Input;
import cvt.cv.ppmbackend.dto.DemandSupplierLineDtos.Response;
import cvt.cv.ppmbackend.entity.Demand;
import cvt.cv.ppmbackend.entity.DemandSupplierLine;
import cvt.cv.ppmbackend.entity.Supplier;
import cvt.cv.ppmbackend.exception.BadRequestException;
import cvt.cv.ppmbackend.exception.ResourceNotFoundException;
import cvt.cv.ppmbackend.repository.DemandRepository;
import cvt.cv.ppmbackend.repository.DemandSupplierLineRepository;
import cvt.cv.ppmbackend.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The suppliers of a demand with their estimated cost (hours or value). Saving the lines also sets
 * the demand's supplier links, so both stay the same set.
 */
@Service
public class DemandSupplierLineService {
    private final DemandSupplierLineRepository lines;
    private final DemandRepository demands;
    private final SupplierRepository suppliers;

    public DemandSupplierLineService(DemandSupplierLineRepository lines, DemandRepository demands,
            SupplierRepository suppliers) {
        this.lines = lines;
        this.demands = demands;
        this.suppliers = suppliers;
    }

    /** Only the lines of suppliers still linked to the demand. */
    @Transactional(readOnly = true)
    public List<Response> list(UUID demandId) {
        Demand demand = demand(demandId);
        Set<UUID> linked = demand.getSuppliers().stream().map(Supplier::getId).collect(Collectors.toSet());
        return lines.findByDemandIdOrderByDisplayOrderAsc(demandId).stream()
                .filter(line -> linked.contains(line.getSupplier().getId()))
                .map(DemandSupplierLineService::map)
                .toList();
    }

    @Transactional
    public List<Response> replace(UUID demandId, List<Input> inputs) {
        Demand demand = demand(demandId);
        List<Input> requested = inputs == null ? List.of() : inputs;
        Set<UUID> seen = new HashSet<>();
        for (Input input : requested) {
            if (input == null || input.supplierId() == null || input.mode() == null) {
                throw new BadRequestException("Cada fornecedor precisa do fornecedor e da base (horas ou valor).");
            }
            if (!seen.add(input.supplierId())) {
                throw new BadRequestException("O mesmo fornecedor não pode repetir-se na demanda.");
            }
        }
        Map<UUID, Supplier> found = suppliers.findAllById(seen).stream()
                .collect(Collectors.toMap(Supplier::getId, Function.identity()));
        for (UUID id : seen) {
            if (!found.containsKey(id)) {
                throw new ResourceNotFoundException("Fornecedor não encontrado: " + id);
            }
        }

        lines.deleteByDemandId(demandId);
        lines.flush();
        for (int index = 0; index < requested.size(); index++) {
            Input input = requested.get(index);
            DemandSupplierLine line = new DemandSupplierLine();
            line.setDemand(demand);
            line.setSupplier(found.get(input.supplierId()));
            line.setMode(input.mode());
            line.setQuantity(input.quantity());
            line.setDisplayOrder(index);
            lines.save(line);
        }
        demand.getSuppliers().clear();
        demand.getSuppliers().addAll(found.values());
        demands.save(demand);
        return list(demandId);
    }

    private Demand demand(UUID demandId) {
        return demands.findByIdAndDeletedAtIsNull(demandId)
                .orElseThrow(() -> new ResourceNotFoundException("Demanda não encontrada: " + demandId));
    }

    private static Response map(DemandSupplierLine line) {
        return new Response(line.getId(), line.getSupplier().getId(), line.getSupplier().getName(), line.getMode(),
                line.getQuantity());
    }
}
