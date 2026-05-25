package scraper.data.author;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class PersonIdentifierId implements Serializable {
    private static final long serialVersionUID = -9128072950345511870L;

    @Column(name = "person_id", nullable = false)
    private Integer personId;

    @Lob
    @Column(name = "domain", nullable = false)
    private String domain;

    @Lob
    @Column(name = "identifier_type", nullable = false)
    private String identifierType;

    @Lob
    @Column(name = "identifier", nullable = false)
    private String identifier;
}