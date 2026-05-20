package scraper.data.proposal.revision;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ProposalRevisionId implements Serializable {
    private static final long serialVersionUID = -5346116926270919087L;
    @Column(name = "project_id", nullable = false)
    private Integer projectId;

    @Lob
    @Column(name = "proposal_id", nullable = false)
    private String proposalId;

    @Column(name = "revision_index", nullable = false)
    private Integer revisionIndex;


}