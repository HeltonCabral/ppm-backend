package cvt.cv.ppmbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PhaseModelDtos {
    private PhaseModelDtos() {
    }

    public record ModelRequest(
            @NotBlank(message = "O código do modelo é obrigatório.")
            @Size(max = 50, message = "O código do modelo não pode exceder 50 caracteres.")
            String code,

            @NotBlank(message = "O nome do modelo é obrigatório.")
            @Size(max = 150, message = "O nome do modelo não pode exceder 150 caracteres.")
            String name,

            @Size(max = 4000, message = "A descrição não pode exceder 4000 caracteres.")
            String description,

            Boolean active) {
    }

    public record PhaseRequest(
            @NotBlank(message = "O código da fase é obrigatório.")
            @Size(max = 50, message = "O código da fase não pode exceder 50 caracteres.")
            String code,

            @NotBlank(message = "O nome da fase é obrigatório.")
            @Size(max = 150, message = "O nome da fase não pode exceder 150 caracteres.")
            String name,

            @Size(max = 4000, message = "A descrição não pode exceder 4000 caracteres.")
            String description,

            @Positive(message = "A sequência deve ser maior que zero.")
            Integer sequence,

            @NotNull(message = "O peso da fase é obrigatório.")
            @DecimalMin(value = "0.00", inclusive = true, message = "O peso da fase não pode ser negativo.")
            @DecimalMax(value = "100.00", inclusive = true, message = "O peso da fase não pode exceder 100%.")
            BigDecimal durationWeightPercentage,

            Boolean active) {
    }

    public record ReorderRequest(
            @NotEmpty(message = "A lista de fases é obrigatória.")
            List<@NotNull(message = "Os identificadores de fase não podem conter valores nulos.") UUID> phaseIds) {
    }

    public record ModelResponse(
            UUID id,
            String code,
            String name,
            String description,
            boolean active,
            BigDecimal totalActiveWeight,
            boolean weightsBalanced,
            List<PhaseResponse> phases,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record PhaseResponse(
            UUID id,
            UUID phaseModelId,
            String code,
            String name,
            String description,
            Integer sequence,
            BigDecimal durationWeightPercentage,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ActiveToggleRequest(
            @NotNull(message = "O estado é obrigatório.")
            Boolean active) {
    }

    public record ModelCreateWithPhases(
            @Valid ModelRequest model,
            @Valid List<PhaseRequest> phases) {
    }
}
