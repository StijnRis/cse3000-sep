package scraper.data.proposal.revision;

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
public class ProposalRevisionAuthorId implements Serializable {
    private static final long serialVersionUID = 6922720706758371017L;
    @Column(name = "project_id", nullable = false)
    private Integer projectId;

    @Lob
    @Column(name = "proposal_id", nullable = false)
    private String proposalId;

    @Column(name = "revision_index", nullable = false)
    private Integer revisionIndex;

    @Column(name = "author_id", nullable = false)
    private Integer authorId;


}