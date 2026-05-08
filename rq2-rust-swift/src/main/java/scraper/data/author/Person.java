package scraper.data.author;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "Person")
public class Person {
    @Id
    @Column(name = "person_id", nullable = false)
    private Integer id;

    @Lob
    @Column(name = "full_name")
    private String fullName;
}