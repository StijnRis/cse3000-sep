package scraper.data.proposal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "RelatedProposal")
public class RelatedProposal {
    @EmbeddedId
    private RelatedProposalId id;

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

    @MapsId("id")
    @JoinColumns({
            @JoinColumn(name = "related_project_id",
                    referencedColumnName = "project_id",
                    nullable = false),
            @JoinColumn(name = "related_proposal_id",
                    referencedColumnName = "proposal_id",
                    nullable = false)})
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Proposal relatedProposal;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private RelatedProposalType type;
}