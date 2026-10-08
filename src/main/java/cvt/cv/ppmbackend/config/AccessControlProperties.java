package cvt.cv.ppmbackend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Controlo de acesso às demandas por direção. Ambas as flags ficam desligadas por defeito: só se ligam
 * depois de os perfis e transactions estarem configurados no hub e as direções atribuídas, senão
 * todos os utilizadores deixam de ver demandas no deploy.
 */
@Component
@ConfigurationProperties(prefix = "ppm.access")
public class AccessControlProperties {

    private final Toggle directionFilter = new Toggle();
    private final Toggle adminGuard = new Toggle();

    /** {@code ppm.access.direction-filter.enabled}: aplica a regra de ver/editar demandas por direção. */
    public Toggle getDirectionFilter() { return directionFilter; }

    /** {@code ppm.access.admin-guard.enabled}: exige a transaction de administração nos endpoints de direções. */
    public Toggle getAdminGuard() { return adminGuard; }

    public boolean directionFilterEnabled() { return directionFilter.isEnabled(); }

    public boolean adminGuardEnabled() { return adminGuard.isEnabled(); }

    public static class Toggle {
        private boolean enabled;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
