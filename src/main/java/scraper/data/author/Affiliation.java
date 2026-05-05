package scraper.data.author;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "Affiliation")
public class Affiliation {
    @EmbeddedId
    private AffiliationId id;

    @MapsId("organisationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "organisation_id", nullable = false)
    private Organisation organisation;

    @MapsId("personId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;


}