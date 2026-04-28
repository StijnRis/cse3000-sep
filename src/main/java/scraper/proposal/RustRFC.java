package scraper.proposal;

import java.net.URL;
import java.util.List;

public class RustRFC extends Proposal {

    public final List<URL> rfcPRs;
    public final List<URL> trackingIssue;

    public RustRFC(String projectName, String proposalIdentifier, String featureName, Status status, String implementedVersion, List<VersionedProposal> versions, List<URL> rfcPRs, List<URL> trackingIssue) {
        super(projectName, proposalIdentifier, featureName, status, implementedVersion, versions);
        this.rfcPRs = rfcPRs;
        this.trackingIssue = trackingIssue;
    }
}
