package scraper.data.proposal.revision;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import scraper.data.proposal.Proposal;

import java.sql.Date;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ProposalRevision")
public class ProposalRevision {
    @EmbeddedId
    private ProposalRevisionId id;

    @MapsId("id")
    @JoinColumns({
            @JoinColumn(name = "project_id",
                    referencedColumnName = "project_id",
                    nullable = false),
            @JoinColumn(name = "proposal_id",
                    referencedColumnName = "proposal_id",
                    nullable = false)})
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Proposal proposal;

    @Lob
    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Lob
    @Column(name = "content")
    private String content;

    @Lob
    @Column(name = "implemented_at_version")
    private String implementedAtVersion;


}