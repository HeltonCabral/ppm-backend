package cvt.cv.ppmbackend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import cvt.cv.ppmbackend.dto.DemandDtos.ReprioritizePortfolioRankRequest;

public class ReprioritizeRequestValidator implements ConstraintValidator<ValidReprioritizeRequest, ReprioritizePortfolioRankRequest> {

    @Override
    public void initialize(ValidReprioritizeRequest annotation) {
    }

    @Override
    public boolean isValid(ReprioritizePortfolioRankRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        String reason = request.reprioritizationReason();
        String justification = request.reprioritizationJustification();

        // Quando o motivo for OTHER, a justificação é obrigatória
        if ("OTHER".equalsIgnoreCase(reason)) {
            if (justification == null || justification.isBlank()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("reprioritizationJustification é obrigatória quando reprioritizationReason é 'OTHER'")
                        .addPropertyNode("reprioritizationJustification")
                        .addConstraintViolation();
                return false;
            }
            if (justification.length() < 10) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("reprioritizationJustification deve ter no mínimo 10 caracteres")
                        .addPropertyNode("reprioritizationJustification")
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
