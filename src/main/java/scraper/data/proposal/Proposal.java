package scraper.data.proposal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import scraper.data.Project;
import scraper.data.author.Person;

import java.net.URL;
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
    private List<String> rfcPrs;

    @Transient
    private List<String> trackingIssues;
}