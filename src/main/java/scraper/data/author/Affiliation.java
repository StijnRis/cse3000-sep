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

    public static Affiliation create(Session session, Organisation org, Person person) {
        Affiliation aff = new Affiliation(new AffiliationId(org.getId(), person.getId()), org, person);

        Transaction tx = session.beginTransaction();
        session.persist(aff);
        tx.commit();

        return aff;
    }

    public static Optional<Affiliation> find(Session session, Organisation org, Person person) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<Affiliation> cq = cb.createQuery(Affiliation.class);
        Root<Affiliation> root = cq.from(Affiliation.class);
        cq.select(root).where(cb.and(
                cb.equal(root.get("id").get("organisationId"), org.getId()),
                cb.equal(root.get("id").get("personId"), person.getId())
        ));

        Query<Affiliation> query = session.createQuery(cq);
        List<Affiliation> results = query.getResultList();

        return Optional.ofNullable(results.isEmpty() ? null : results.getFirst());
    }
}