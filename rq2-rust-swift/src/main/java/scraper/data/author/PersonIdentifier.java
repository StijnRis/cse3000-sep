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
@Table(name = "PersonIdentifier")
public class PersonIdentifier {
    @EmbeddedId
    private PersonIdentifierId id;

    @MapsId("personId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    public static PersonIdentifier createIfMissing(Session session, Person person, String domain, String identifier_type, String identifier) {
        Optional<PersonIdentifier> result = PersonIdentifier.findByDomain(session, domain, identifier_type, identifier);

        return result.orElseGet(() -> PersonIdentifier.create(session, person, domain, identifier_type, identifier));
    }

    public static PersonIdentifier create(Session session, Person person, String domain, String identifier_type, String identifier) {
        PersonIdentifierId pUId = new PersonIdentifierId(person.getId(), domain, identifier_type, identifier);
        PersonIdentifier pU = new PersonIdentifier(pUId, person);

        Transaction tx = session.beginTransaction();
        session.persist(pU);
        tx.commit();

        return pU;
    }

    public static Optional<PersonIdentifier> findByDomain(Session session, String domain, String identifier_type, String identifier) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<PersonIdentifier> cq = cb.createQuery(PersonIdentifier.class);
        Root<PersonIdentifier> root = cq.from(PersonIdentifier.class);
        cq.select(root).where(cb.and(
                cb.equal(root.get("id").get("domain"), domain),
                cb.equal(root.get("id").get("identifierType"), identifier_type),
                cb.equal(root.get("id").get("identifier"), identifier)
        ));

        Query<PersonIdentifier> query = session.createQuery(cq);
        List<PersonIdentifier> results = query.getResultList();

        if(results.isEmpty()) return Optional.empty();
        return Optional.of(results.getFirst());
    }
}