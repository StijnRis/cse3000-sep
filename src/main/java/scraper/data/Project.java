package scraper.data;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "Project")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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


    public Project(String projectName, String enhancementProposalName, String copyright) {
        this.projectName = projectName;
        this.enhancementProposalName = enhancementProposalName;
        this.copyright = copyright;
    }
}