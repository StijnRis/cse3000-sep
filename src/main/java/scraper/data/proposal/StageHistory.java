package scraper.data.proposal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;

@Getter
@Setter
@Entity
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
    private Instant createdAt;


}