package cvt.cv.ppmbackend.entity;

import cvt.cv.ppmbackend.enums.CommitteeStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "committees")
@Getter
@Setter
@NoArgsConstructor
public class Committee extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "name_key", nullable = false, unique = true, length = 300)
    private String nameKey;

    @Column(nullable = false, length = 4000)
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CommitteeStatus status;

    @Column(name = "is_strategic_committee", nullable = false)
    @ColumnDefault("false")
    private boolean isStrategicCommittee;

    @Column(name = "minimum_budget", precision = 18, scale = 2)
    private BigDecimal minimumBudget;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "committee_members", joinColumns = @JoinColumn(name = "committee_id"))
    @OrderColumn(name = "member_order")
    @Column(name = "member_name", nullable = false, length = 200)
    private List<String> members = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "committee_directions", joinColumns = @JoinColumn(name = "committee_id"))
    @OrderColumn(name = "direction_order")
    @Column(name = "direction_name", nullable = false, length = 200)
    private List<String> directions = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "committee_demand_types", joinColumns = @JoinColumn(name = "committee_id"))
    @OrderColumn(name = "demand_type_order")
    @Column(name = "demand_type_name", nullable = false, length = 200)
    private List<String> demandTypes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "committee_domains", joinColumns = @JoinColumn(name = "committee_id"))
    @OrderColumn(name = "domain_order")
    @Column(name = "domain_name", nullable = false, length = 200)
    private List<String> domains = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "committee_risk_levels", joinColumns = @JoinColumn(name = "committee_id"))
    @OrderColumn(name = "risk_level_order")
    @Column(name = "risk_level_name", nullable = false, length = 100)
    private List<String> riskLevels = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
