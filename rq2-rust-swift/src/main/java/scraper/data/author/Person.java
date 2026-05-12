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
@Table(name = "Person")
public class Person {
    @Id
    @Column(name = "person_id", nullable = false)
    private Integer id;

    @Lob
    @Column(name = "full_name")
    private String fullName;

    public Person save(Session session) {
        Transaction tx = session.beginTransaction();
        session.persist(this);
        tx.commit();

        return this;
    }

    public static Person create(Session session, int id, String fullName) {
        Person person = new Person(id, fullName);

        Transaction tx = session.beginTransaction();
        session.persist(person);
        tx.commit();

        return person;
    }

    public static Optional<Person> findAuthorById(Session session, int id) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<Person> cq = cb.createQuery(Person.class);
        Root<Person> root = cq.from(Person.class);
        cq.select(root).where(cb.equal(root.get("id"), id));

        Query<Person> query = session.createQuery(cq);
        List<Person> results = query.getResultList();

        return Optional.ofNullable(results.isEmpty() ? null : results.getFirst());
    }

    public static Optional<Person> findAuthorByName(Session session, String name) {
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<Person> cq = cb.createQuery(Person.class);
        Root<Person> root = cq.from(Person.class);
        cq.select(root).where(cb.equal(root.get("fullName"), name));

        Query<Person> query = session.createQuery(cq);
        List<Person> results = query.getResultList();

        return Optional.ofNullable(results.isEmpty() ? null : results.getFirst());
    }

    public static Optional<Person> findAuthorByEmail(Session session, String email) {
        return PersonUsername.findByDomain(session, "email", email)
                .map(PersonUsername::getPerson);
    }

    public static Optional<Person> findAuthorByGitHubName(Session session, String gitHubName) {
        return PersonUsername.findByDomain(session, "github",  gitHubName)
                .map(PersonUsername::getPerson);
    }
}