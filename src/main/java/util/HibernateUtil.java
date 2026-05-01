package util;

import lombok.Getter;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import scraper.data.Project;
import scraper.data.author.Organisation;
import scraper.data.author.Person;
import scraper.data.author.PersonUsername;

public class HibernateUtil {

    @Getter
    private SessionFactory sessionFactory;

    public HibernateUtil(String sqliteLocation) {
        try {
                Configuration cfg = new Configuration().configure();
                cfg.setJdbcUrl("jdbc:sqlite:" + sqliteLocation);
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