package scraper.data.author;

import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "PersonUsername")
public class PersonUsername {
    @EmbeddedId
    private PersonUsernameId id;

    @MapsId("personId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    public static PersonUsername create(Session session, Person person, String domain, String username) {
        PersonUsernameId pUId = new PersonUsernameId(person.getId(), domain, username);
        PersonUsername pU = new PersonUsername(pUId, person);

        Transaction tx = session.beginTransaction();
        session.persist(pU);
        tx.commit();

        return pU;
    }

    public static Optional<PersonUsername> findByDomain(Session session, String domain, String username) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<PersonUsername> cq = cb.createQuery(PersonUsername.class);
        Root<PersonUsername> root = cq.from(PersonUsername.class);
        cq.select(root).where(cb.and(
                cb.equal(root.get("id").get("domain"), domain),
                cb.equal(root.get("id").get("username"), username)
        ));

        Query<PersonUsername> query = session.createQuery(cq);
        List<PersonUsername> results = query.getResultList();

        if(results.isEmpty()) return Optional.empty();
        return Optional.of(results.getFirst());
    }
}