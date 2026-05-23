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

import java.sql.Date;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "StageHistory")
public class StageHistory {
    @EmbeddedId
    private StageHistoryId id;

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
    @Enumerated(EnumType.STRING)
    @Column(name = "normalised_status", nullable = false)
    private NormalizedStatus normalizedStatus;

    @Lob
    @Column(name = "raw_status")
    private String rawStatus;

    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    public static StageHistory create(Session session, Proposal proposal, int stageIndex, String rawStatus, NormalizedStatus status, Date createdAt) {
        StageHistoryId shId = new StageHistoryId(proposal.getProject().getId(), proposal.getId().getProposalId(), stageIndex);
        StageHistory sh = new StageHistory(shId, proposal, status, rawStatus, createdAt);

        Transaction tx = session.beginTransaction();
        session.persist(sh);
        tx.commit();

        return sh;
    }
}