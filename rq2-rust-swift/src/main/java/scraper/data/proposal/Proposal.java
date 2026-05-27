package scraper.data.proposal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import scraper.data.Project;
import scraper.data.author.Person;
import scraper.parser.GitHubService;

import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "Proposal")
public class Proposal {
    @EmbeddedId
    private ProposalId id;

    @MapsId("projectId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Lob
    @Column(name = "topic")
    private String topic;

    @Lob
    @Column(name = "proposal_type")
    private String proposalType;

    @Transient
    private String fileIdentifier;

    @Transient
    private List<GitHubService.PullRequest> pullRequests;

    @Transient
    private String lastCommitHash;

    public Proposal update(Session session) {
        Transaction tx = session.beginTransaction();
        session.persist(this);
        tx.commit();

        return this;
    }

    public void drop(Session session) {
        Transaction tx = session.beginTransaction();
        session.remove(this);
        tx.commit();
    }

    public static Proposal create(Session session, Project project, String proposalId, String topic, String proposalType, String fileIdentifier, List<GitHubService.PullRequest> pullRequests, String lastCommitHash) {
        ProposalId pId = new ProposalId(project.getId(), proposalId);

        Proposal proposal = new Proposal(pId, project, topic, proposalType, fileIdentifier, pullRequests, lastCommitHash);

        Transaction tx = session.beginTransaction();
        session.persist(proposal);
        tx.commit();

        return proposal;
    }
}