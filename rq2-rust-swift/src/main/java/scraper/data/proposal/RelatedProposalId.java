package scraper.data.proposal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class RelatedProposalId implements Serializable {
    private static final long serialVersionUID = -2981331755445916185L;
    @Column(name = "project_id", nullable = false)
    private Integer projectId;

    @Lob
    @Column(name = "proposal_id", nullable = false)
    private String proposalId;

    @Column(name = "related_project_id", nullable = false)
    private Integer relatedProjectId;

    @Lob
    @Column(name = "related_proposal_id", nullable = false)
    private String relatedProposalId;


}