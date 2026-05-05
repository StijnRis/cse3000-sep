package scraper.data.author;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "PersonUsername")
public class PersonUsername {
    @EmbeddedId
    private PersonUsernameId id;

    @MapsId("personId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Lob
    @Column(name = "real_name")
    private String realName;


}