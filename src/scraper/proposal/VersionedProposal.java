package scraper.proposal;

import java.time.LocalDate;

public class VersionedProposal {

    public final Status status;
    public final LocalDate timestamp;
    public final String content;
    public final String implementationVersion;
    public final String projectRevisionTag;

    public VersionedProposal(Status status, LocalDate timestamp, String content, String implementationVersion, String projectRevisionTag) {
        this.status = status;
        this.timestamp = timestamp;
        this.content = content;
        this.implementationVersion = implementationVersion;
        this.projectRevisionTag = projectRevisionTag;
    }
}
