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
import org.hibernate.query.Query;

import java.util.List;
import java.util.Optional;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "Organisation")
public class Organisation {
    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "organisation_id", nullable = false)
    private Integer id;

    @Lob
    @Column(name = "organisation_name", nullable = false)
    private String organisationName;

    public static Organisation create(Session session, int id, String organisationName) {
        Organisation org = new Organisation(id, organisationName);

        Transaction tx = session.beginTransaction();
        session.persist(org);
        tx.commit();

        return org;
    }

    public static Optional<Organisation> findByName(Session session, String organisationName) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<Organisation> cq = cb.createQuery(Organisation.class);
        Root<Organisation> root = cq.from(Organisation.class);
        cq.select(root).where(cb.equal(root.get("organisationName"), organisationName));

        Query<Organisation> query = session.createQuery(cq);
        List<Organisation> results = query.getResultList();

        return Optional.ofNullable(results.isEmpty() ? null : results.getFirst());
    }
}