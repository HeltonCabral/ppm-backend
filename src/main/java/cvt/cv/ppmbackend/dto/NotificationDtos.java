package cvt.cv.ppmbackend.dto;

import java.time.LocalDate;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record Response(String id, String kind, String severity, String title, String detail,
            String href, String projectCode, LocalDate at, boolean read) {
        public Response withRead(boolean value) {
            return new Response(id, kind, severity, title, detail, href, projectCode, at, value);
        }
    }

    public record ReadRequest(List<String> ids) {
    }
}
