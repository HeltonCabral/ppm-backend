package cvt.cv.ppmbackend.controller;

import cvt.cv.ppmbackend.dto.NotificationDtos.ReadRequest;
import cvt.cv.ppmbackend.dto.NotificationDtos.Response;
import cvt.cv.ppmbackend.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public List<Response> list() { return notifications.list(); }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@RequestBody ReadRequest request) { notifications.markRead(request); }
}
