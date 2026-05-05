package scraper.data.author;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "Organisation")
public class Organisation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "organisation_id", nullable = false)
    private Integer id;

    @Lob
    @Column(name = "organisation_name", nullable = false)
    private String organisationName;


}