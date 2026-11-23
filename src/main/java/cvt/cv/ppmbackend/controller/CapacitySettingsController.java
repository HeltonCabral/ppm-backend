package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.CapacitySettingsDto;
import cvt.cv.ppmbackend.service.CapacitySettingsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/capacity/settings")
public class CapacitySettingsController {
    private final CapacitySettingsService settings;

    public CapacitySettingsController(CapacitySettingsService settings) { this.settings = settings; }

    @GetMapping
    public CapacitySettingsDto get() { return settings.get(); }

    @PutMapping
    public CapacitySettingsDto update(@RequestBody CapacitySettingsDto request) { return settings.update(request); }
}
