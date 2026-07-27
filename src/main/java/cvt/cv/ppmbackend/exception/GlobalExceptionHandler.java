package cvt.cv.ppmbackend.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(DomainException.class)
    ResponseEntity<Map<String,Object>> domain(DomainException e, jakarta.servlet.http.HttpServletRequest request) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("code",e.getCode(),"message",e.getMessage(),"details",e.getDetails(),"traceId",Optional.ofNullable(request.getHeader("X-Trace-Id")).orElse(UUID.randomUUID().toString())));
    }
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException e) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", e.getMessage(), Map.of());
    }

    @ExceptionHandler({ BadRequestException.class, DataIntegrityViolationException.class })
    ResponseEntity<ApiError> badRequest(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", e.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Dados inválidos.", fields);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> type(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Valor inválido para " + e.getName(), Map.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus s, String code, String m, Map<String, String> f) {
        return ResponseEntity.status(s).body(new ApiError(Instant.now(), s.value(), code, m, f, s.getReasonPhrase()));
    }
}
