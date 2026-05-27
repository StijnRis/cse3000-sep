package scraper.data;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "Project")
public class Project {
    @Id
    @Column(name = "project_id", nullable = false)
    private Integer id;

    @Lob
    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Lob
    @Column(name = "enhancement_proposal_name", nullable = false)
    private String enhancementProposalName;

    @Lob
    @Column(name = "copyright", nullable = false)
    private String copyright;
}