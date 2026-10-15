package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.dto.AccessDtos.DirectionRef;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * O que o backend resolveu para o utilizador do pedido: identificador (minúsculas), permissões do hub
 * (minúsculas) e direções em vigor.
 */
public record UserAccess(String userName, Set<String> permissions, List<DirectionRef> directions) {

    public static final String VIEW_DIRECTION = "demanda_ver_direcao";
    public static final String EDIT_DIRECTION = "demanda_editar_direcao";
    public static final String VIEW_ALL = "demanda_ver_todas";
    public static final String EDIT_ALL = "demanda_editar_todas";
    public static final String ADMIN_USER_DIRECTIONS = "admin_utilizadores_direcao";
    public static final String PROJECT_EDIT_ALL = "projeto_editar_todos";
    public static final String PROJECT_VIEW_ALL = "projeto_ver_todos";

    /** Acesso de processos internos, sem pedido HTTP (jobs, inicializadores): vê e edita tudo. */
    public static UserAccess system() {
        return new UserAccess("system", Set.of(VIEW_ALL, EDIT_ALL), List.of());
    }

    public boolean has(String permission) {
        return permissions.contains(permission.toLowerCase(Locale.ROOT));
    }

    public boolean editAll() {
        return has(EDIT_ALL);
    }

    /** Quem edita todas também as vê. */
    public boolean viewAll() {
        return has(VIEW_ALL) || editAll();
    }

    /** Códigos das direções em vigor, em maiúsculas. */
    public Set<String> directionCodes() {
        return directions.stream()
                .map(DirectionRef::code)
                .map(code -> code.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean belongsTo(String directionCode) {
        return directionCode != null && directionCodes().contains(directionCode.trim().toUpperCase(Locale.ROOT));
    }

    public boolean isCreator(String createdBy) {
        return createdBy != null && createdBy.trim().equalsIgnoreCase(userName);
    }
}
