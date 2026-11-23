package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.dto.DemandDtos.Create;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** A demand imported from Excel brings no problem statement nor desired outcome; only the title is required. */
class DemandCreateValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void createWithoutProblemStatementNorDesiredOutcomeIsValid() {
        assertThat(violations(create("Demanda importada"))).isEmpty();
    }

    @Test
    void titleIsStillRequired() {
        assertThat(violations(create(" "))).containsExactly("title");
    }

    private static Set<String> violations(Create create) {
        return validator.validate(create).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static Create create(String title) {
        return new Create(
                title, null, null, null,
                null, null,
                null, null,
                null, null,
                null, null, null,
                null, "PROJ_TI", null, null, null, null, null, null,
                null, null,
                null, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null,
                null, null, null);
    }
}
