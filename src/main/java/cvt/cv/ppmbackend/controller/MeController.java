package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.AccessDtos.MeAccessResponse;
import cvt.cv.ppmbackend.service.UserAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class MeController {
    private final UserAccessService userAccess;

    public MeController(UserAccessService userAccess) {
        this.userAccess = userAccess;
    }

    /** O que o backend resolveu para o utilizador atual: direções em vigor, permissões e flag. */
    @GetMapping("/access")
    public MeAccessResponse access() {
        return userAccess.me();
    }
}
