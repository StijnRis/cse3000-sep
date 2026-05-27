package scraper.data.proposal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Lob;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@Embeddable
@NoArgsConstructor
public class ProposalId implements Serializable {
    private static final long serialVersionUID = 481209689491167753L;
    @Column(name = "project_id", nullable = false)
    private Integer projectId;

    @Lob
    @Column(name = "proposal_id", nullable = false)
    private String proposalId;

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof ProposalId that)) return false;

        return Objects.equals(projectId, that.projectId) && Objects.equals(proposalId, that.proposalId);
    }
}