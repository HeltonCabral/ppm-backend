package cvt.cv.ppmbackend.dto;

import java.util.List;
import java.util.UUID;

/**
 * Acrescentar ({@code POST}) ou alterar ({@code PUT}) um perfil previsto do projeto.
 *
 * @param profileId        perfil de capacidade; obrigatório no {@code POST}, ignorado no {@code PUT}
 *                         (não se troca o perfil de um registo: remove-se e acrescenta-se outro)
 * @param requiredQuantity n.º de pessoas, inteiro ≥ 1
 * @param phases           nomes das fases, pela ordem pretendida; vazio = todo o projeto
 */
public record ProjectProfileRequest(UUID profileId, Integer requiredQuantity, List<String> phases) {
}
