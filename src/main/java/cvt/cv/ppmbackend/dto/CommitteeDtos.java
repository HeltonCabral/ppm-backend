package cvt.cv.ppmbackend.dto;

import cvt.cv.ppmbackend.enums.CommitteeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommitteeDtos {
    private CommitteeDtos() {
    }

    public record Request(
            @NotBlank(message = "O nome do comité é obrigatório.")
            @Size(max = 150, message = "O nome do comité não pode exceder 150 caracteres.")
            String name,

            @Size(max = 4000, message = "A descrição não pode exceder 4000 caracteres.")
            String description,

            @NotBlank(message = "O estado é obrigatório.")
            @Schema(allowableValues = { "ACTIVE", "INACTIVE" })
            String status,

            boolean isStrategicCommittee,

            @NotNull(message = "A lista de membros é obrigatória.")
            @Valid
            List<@NotBlank(message = "Os membros não podem conter valores vazios.")
                    @Size(max = 200, message = "Cada membro não pode exceder 200 caracteres.") String> members,

            @NotNull(message = "A lista de direções é obrigatória.")
            @Valid
            List<@NotBlank(message = "As direções não podem conter valores vazios.")
                    @Size(max = 200, message = "Cada direção não pode exceder 200 caracteres.") String> directions,

            @NotNull(message = "A lista de tipos de demanda é obrigatória.")
            @Valid
            List<@NotBlank(message = "Os tipos de demanda não podem conter valores vazios.")
                    @Size(max = 200, message = "Cada tipo de demanda não pode exceder 200 caracteres.") String> demandTypes,

            @NotNull(message = "A lista de domínios é obrigatória.")
            @Valid
            List<@NotBlank(message = "Os domínios não podem conter valores vazios.")
                    @Size(max = 200, message = "Cada domínio não pode exceder 200 caracteres.") String> domains,

            @NotNull(message = "A lista de níveis de risco é obrigatória.")
            @Valid
            List<@NotBlank(message = "Os níveis de risco não podem conter valores vazios.")
                    @Size(max = 100, message = "Cada nível de risco não pode exceder 100 caracteres.") String> riskLevels,

            @PositiveOrZero(message = "O orçamento mínimo deve ser maior ou igual a zero.")
            BigDecimal minimumBudget) {
    }

    public record Response(
            UUID id,
            String name,
            String description,
            CommitteeStatus status,
            boolean isStrategicCommittee,
            List<String> members,
            List<String> directions,
            List<String> demandTypes,
            List<String> domains,
            List<String> riskLevels,
            BigDecimal minimumBudget,
            Instant createdAt,
            Instant updatedAt) {
    }
}
