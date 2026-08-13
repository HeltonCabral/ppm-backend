package cvt.cv.ppmbackend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ReprioritizeRequestValidator.class)
public @interface ValidReprioritizeRequest {
    String message() default "Quando reprioritizationReason é 'OTHER', reprioritizationJustification é obrigatória";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
