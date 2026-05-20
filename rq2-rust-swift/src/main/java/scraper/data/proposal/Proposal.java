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

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "proposer_id")
    private Person proposer;

    @Lob
    @Column(name = "topic")
    private String topic;

    @Lob
    @Column(name = "proposal_type")
    private String proposalType;

    @Transient
    private GitHubService.PullRequest pullRequest;

    @Transient
    private String lastCommitHash;

    public static Proposal create(Session session, Project project, String proposalId, Person proposer, String topic, String proposalType, GitHubService.PullRequest pullRequest, String lastCommitHash) {
        ProposalId pId = new ProposalId(project.getId(), proposalId);

        Proposal proposal = new Proposal(pId, project, proposer, topic, proposalType, pullRequest, lastCommitHash);

        Transaction tx = session.beginTransaction();
        session.persist(proposal);
        tx.commit();

        return proposal;
    }
}