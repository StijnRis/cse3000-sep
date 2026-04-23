package scraper.proposal;

import java.util.List;

public class Proposal {

    private int proposalID = 0;
    public final String projectName;

    public final String proposalIdentifier;
    public final String featureName;
    public final Status status;
    public final String implementedVersion;

    //proposar
    //authors
    //priority track
    //topic
    //prop type

    public final List<VersionedProposal> versions;

    public Proposal(String projectName, String proposalIdentifier, String featureName, Status status, String implementedVersion, List<VersionedProposal> versions) {
        this.projectName = projectName;
        this.proposalIdentifier = proposalIdentifier;
        this.featureName = featureName;
        this.status = status;
        this.implementedVersion = implementedVersion;
        this.versions = versions;
    }
}
