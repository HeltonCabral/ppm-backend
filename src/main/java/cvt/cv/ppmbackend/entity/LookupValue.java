package cvt.cv.ppmbackend.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lookup_values", uniqueConstraints = @UniqueConstraint(name = "uk_lookup_category_code", columnNames = {
        "category", "code" }))
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class LookupValue extends BaseEntity {
    @Column(nullable = false, length = 50)
    private String category;
    @Column(nullable = false, length = 80)
    private String code;
    @Column(nullable = false, length = 150)
    private String label;
    @Column(name = "sort_order")
    private Integer sortOrder;
    private Boolean active = true;
}
