package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Recusa (409) apagar um item da estrutura estratégica que ainda é usado. */
final class StrategicUsage {
    private StrategicUsage() {
    }

    /** Ex.: "Não é possível eliminar o pilar Clientes: tem 2 elementos estratégicos e 1 projetos associados." */
    static void requireUnused(String what, String name, String code, Map<String, Long> usages) {
        Map<String, Long> used = new LinkedHashMap<>();
        usages.forEach((label, count) -> {
            if (count != null && count > 0) used.put(label, count);
        });
        if (used.isEmpty()) {
            return;
        }
        String detail = used.entrySet().stream().map(e -> e.getValue() + " " + e.getKey())
                .collect(Collectors.joining(", "));
        throw new DomainException(HttpStatus.CONFLICT, code,
                "Não é possível eliminar " + what + " " + name + ": tem " + detail + " associados.",
                new LinkedHashMap<>(used));
    }
}
