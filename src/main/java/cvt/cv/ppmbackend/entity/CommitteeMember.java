package cvt.cv.ppmbackend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CommitteeMember {

    @Column(name = "member_name", nullable = false, length = 200)
    private String name;

    @Column(name = "member_code", nullable = false, length = 100)
    private String code;
}