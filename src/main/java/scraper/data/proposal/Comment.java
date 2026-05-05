package scraper.data.proposal;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import scraper.data.author.Person;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "Comment")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "author_id")
    private Person author;

    @JoinColumns({
            @JoinColumn(name = "project_id",
                    referencedColumnName = "project_id"),
            @JoinColumn(name = "proposal_id",
                    referencedColumnName = "proposal_id")})
    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    private Proposal proposal;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.RESTRICT)
    @JoinColumn(name = "comment_on_comment_id")
    private Comment commentOnComment;

    @Column(name = "created_at")
    private Instant createdAt;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;


}