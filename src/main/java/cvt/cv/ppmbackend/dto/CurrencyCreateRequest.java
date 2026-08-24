package cvt.cv.ppmbackend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;

public record CurrencyCreateRequest (
        @NotBlank
        String code,
        @NotBlank
        String name,
        String symbol,
        BigDecimal rateToEcv
) {

        }
