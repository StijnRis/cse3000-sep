package scraper.data.author;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class AffiliationId implements Serializable {
    private static final long serialVersionUID = -3663382611761012955L;
    @Column(name = "organisation_id", nullable = false)
    private Integer organisationId;

    @Column(name = "person_id", nullable = false)
    private Integer personId;


}