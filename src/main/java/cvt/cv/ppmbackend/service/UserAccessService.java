package cvt.cv.ppmbackend.service;

import cvt.cv.ppmbackend.config.AccessControlProperties;
import cvt.cv.ppmbackend.dto.AccessDtos.DirectionRef;
import cvt.cv.ppmbackend.dto.AccessDtos.MeAccessResponse;
import cvt.cv.ppmbackend.exception.DomainException;
import cvt.cv.ppmbackend.repository.UserDirectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolve o utilizador do pedido. As permissões vêm do cabeçalho {@code X-User-Permissions}, posto pelo
 * proxy do frontend a partir das transactions do hub; as direções vêm da tabela {@code user_direction}.
 * O resultado fica guardado no pedido para não repetir a consulta por cada demanda mapeada.
 */
@Service
public class UserAccessService {
    public static final String USER_HEADER = "X-User-Id";
    public static final String PERMISSIONS_HEADER = "X-User-Permissions";
    private static final String REQUEST_ATTRIBUTE = UserAccessService.class.getName() + ".current";

    private final UserDirectionRepository userDirections;
    private final AccessControlProperties properties;

    public UserAccessService(UserDirectionRepository userDirections, AccessControlProperties properties) {
        this.userDirections = userDirections;
        this.properties = properties;
    }

    public UserAccess current() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return UserAccess.system();
        }
        Object cached = request.getAttribute(REQUEST_ATTRIBUTE);
        if (cached instanceof UserAccess access) {
            return access;
        }
        UserAccess access = resolve(request.getHeader(USER_HEADER), request.getHeader(PERMISSIONS_HEADER));
        request.setAttribute(REQUEST_ATTRIBUTE, access);
        return access;
    }

    public UserAccess resolve(String userHeader, String permissionsHeader) {
        String userName = normalizeUserName(userHeader);
        List<DirectionRef> directions = userDirections.findActiveByUserName(userName, LocalDate.now()).stream()
                .map(ud -> new DirectionRef(ud.getDirectionCode(), ud.getDirectionName()))
                .toList();
        return new UserAccess(userName, parsePermissions(permissionsHeader), directions);
    }

    public MeAccessResponse me() {
        UserAccess access = current();
        return new MeAccessResponse(access.userName(), access.directions(),
                access.permissions().stream().sorted().toList(), access.viewAll(), access.editAll(),
                properties.directionFilterEnabled());
    }

    /** Endpoints de administração das direções: só exige a transaction quando a proteção está ligada. */
    public void requireAdmin() {
        if (!properties.adminGuardEnabled()) {
            return;
        }
        if (!current().has(UserAccess.ADMIN_USER_DIRECTIONS)) {
            throw new DomainException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "Não tem permissão para gerir utilizadores por direção.", Map.of());
        }
    }

    public static String normalizeUserName(String value) {
        return value == null || value.isBlank() ? "system" : value.trim().toLowerCase(Locale.ROOT);
    }

    static Set<String> parsePermissions(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .map(code -> code.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return Objects.requireNonNull(attributes.getRequest());
        }
        return null;
    }
}
