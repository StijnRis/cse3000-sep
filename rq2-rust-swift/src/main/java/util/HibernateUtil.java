package util;

import lombok.Getter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import scraper.data.*;
import scraper.data.proposal.*;
import scraper.data.proposal.revision.*;
import scraper.data.author.*;

public class HibernateUtil {

    @Getter
    private SessionFactory sessionFactory;

    public HibernateUtil(String sqliteLocation) {
        try {
                Configuration cfg = new Configuration().configure();
                cfg.setJdbcUrl("jdbc:sqlite:" + sqliteLocation);
                cfg.addAnnotatedClasses(
                        Project.class,
                        Person.class,
                        Organisation.class,
                        Affiliation.class,
                        PersonIdentifier.class,
                        Proposal.class,
                        RelatedProposal.class,
                        ProposalStatus.class,
                        Comment.class,
                        ProposalRevision.class,
                        ProposalRevisionAuthor.class
                );
                StandardServiceRegistry ssRegistry = new StandardServiceRegistryBuilder().applySettings(cfg.getProperties()).build();
                sessionFactory = cfg.buildSessionFactory(ssRegistry);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public Session getSession() {
        return sessionFactory.openSession();
    }
}