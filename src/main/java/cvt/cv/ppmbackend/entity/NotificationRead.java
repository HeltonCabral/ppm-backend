package cvt.cv.ppmbackend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notification_read", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_read_user_alert", columnNames = { "user_name", "alert_id" }),
        indexes = @Index(name = "idx_notification_read_user", columnList = "user_name"))
@Getter
@Setter
@NoArgsConstructor
public class NotificationRead extends BaseEntity {
    @Column(name = "user_name", nullable = false, length = 100)
    private String userName;
    @Column(name = "alert_id", nullable = false, length = 300)
    private String alertId;
    @Column(name = "read_at", nullable = false)
    private Instant readAt;
}
