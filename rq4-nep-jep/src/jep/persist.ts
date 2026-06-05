import { Persister, realNameDomain, RelatedProposalSQL } from "../persist.ts"; 
import type { JEP } from "./scraper.ts";
import { sortedBy } from "../util.ts";

const openjdkDomain = 'openjdk.org';

export class JEPPersister extends Persister<JEP> {
  project = {
    project_id: 6,
    project_name: 'OpenJDK',
    enhancement_proposal_name: 'JEP',
    copyright: 'GPLv2',
  };

  normalizeStatus(status: JEP['status']) {
    return status ? (({
      'Draft': 'draft',
      'Posted': 'review',
      'Submitted': 'review',
      'Candidate': 'review',
      'Funded': 'review',
      'Completed': 'accepted',
      'Delivered': 'accepted',
      'Withdrawn': 'withdrawn',
      'Rejected': 'rejected',
      'Active': 'accepted',
      'Closed / Delivered': 'accepted',
      'Closed / Withdrawn': 'withdrawn',
      'Proposed to Target': 'review',
      'Integrated': 'accepted',
      'Targeted': 'review',
      'Open': 'review',
      'In Progress': 'review',
      'Closed': 'unknown',
      'Proposed to Drop': 'review',
      'New': 'draft',
    } as const)[status] || 'unknown') : 'unknown';
  }

  persistPeople(jeps: JEP[]) {
    for(const jep of sortedBy(jeps, j => j.id)) {
      if(jep.owner) {
        const {person_id} = this.ensurePerson(openjdkDomain, 'username', jep.owner.username, jep.owner.name);
        this.addPersonDetails(person_id, openjdkDomain, 'username', jep.owner.username);
        this.addPersonDetails(person_id, openjdkDomain, 'display_name', jep.owner.name);
      }
      for(const author of sortedBy(jep.authors, a => a)) {
        const {person_id} = this.ensurePerson(openjdkDomain, 'display_name', author, author);
        this.addPersonDetails(person_id, openjdkDomain, 'display_name', author); //authors are only listed by name
      }
      for(const revision of sortedBy(jep.revisions, r => new Date(r.created).getTime())) {
        const {person_id} = this.ensurePerson(openjdkDomain, 'username', revision.author.username, revision.author.name);
        this.addPersonDetails(person_id, openjdkDomain, 'username', revision.author.username);
        this.addPersonDetails(person_id, openjdkDomain, 'display_name', revision.author.name);
      }
      for(const comment of sortedBy(jep.comments, c => c.id)) {
        const {person_id} = this.ensurePerson(openjdkDomain, 'username', comment.author.username, comment.author.name);
        this.addPersonDetails(person_id, openjdkDomain, 'username', comment.author.username);
        this.addPersonDetails(person_id, openjdkDomain, 'display_name', comment.author.name);
      }
    }
  }

  // private cleanTitle(title: string) {
  //   return title.replace(/^JEP\s+\S*\s*[:-]|^JEP:|^JEP XXX|^Draft\s+JEP:/, '').trim();
  // }

  persistSEPs(jeps: JEP[]) {
    for(const jep of jeps) {
      const id = `${jep.id}`;
      const proposal = {
        project_id: this.projID(),
        project: this.project,
        proposal_id: id,
        // proposer_id: final.owner ? this.usernameToID.get([openjdkDomain, final.owner.username]) : undefined,
        proposal_type: jep.type,
      };
      this.db.proposal.set(`${this.projID()} ${id}`, proposal);

      let lastContent: string = jep.content;
      let lastTitle: string = jep.title;
      let lastVersion: string | undefined = jep.release;
      // revisions in reverse, to get the original values
      for(const rev of jep.revisions.toReversed()) {
        if(rev.kind === 'description') lastContent = rev.from ?? '';
        else if(rev.kind === 'summary') lastTitle = rev.from ?? '';
        else if(rev.kind === 'version') lastVersion = rev.from ?? '';
      }
      this.db.proposal_revision.set(`${this.projID()} ${id} 0`, {
        project_id: proposal.project_id,
        proposal_id: proposal.proposal_id,
        proposal,
        revision_index: 0,
        title: lastTitle,
        created_at: jep.created.toISOString(),
        content: lastContent,
        implemented_at_version: lastVersion,
        authors: [this.db.person.get(this.personDetailsToID.get(`${openjdkDomain} username ${jep.owner.username}`)!)!],
      });
      let revision_index = 1;

      let last_status: JEP['status'] = 'Draft';
      this.db.proposal_status.set(`${this.projID()} ${id} 0`, {
        project_id: proposal.project_id,
        proposal_id: proposal.proposal_id,
        proposal,
        status_index: 0,
        normalised_status: this.normalizeStatus(last_status),
        raw_status: last_status,
        created_at: jep.created.toISOString(),
      });
      let status_index = 1; // increment only when status actually changes from one revision to the next

      for(const rev of jep.revisions) {
        if(rev.kind === 'status' || rev.kind == 'resolution') {
          if(!(rev.kind === 'status' && rev.to === 'Closed')
              && !(rev.kind === 'resolution' && rev.to === undefined)
              && rev.to !== last_status) {
            last_status = rev.to as JEP['status'];
            this.db.proposal_status.set(`${this.projID()} ${id} ${status_index}`, {
              project_id: proposal.project_id,
              proposal_id: proposal.proposal_id,
              proposal,
              status_index,
              normalised_status: this.normalizeStatus(last_status),
              raw_status: last_status || '',
              created_at: rev.created.toISOString(),
            });
            status_index++;
          }
        } else if (rev.kind) {
          if(rev.kind === 'description') lastContent = rev.to ?? '';
          else if(rev.kind === 'summary') lastTitle = rev.to ?? '';
          else if(rev.kind === 'version') lastVersion = rev.to;
          const revisionKey = `${this.projID()} ${id} ${revision_index}`;
          const authorID = this.personDetailsToID.get(`${openjdkDomain} username ${rev.author.username}`)!;
          this.db.proposal_revision.set(revisionKey, {
            project_id: proposal.project_id,
            proposal_id: proposal.proposal_id,
            proposal,
            revision_index,
            title: lastTitle,
            created_at: rev.created.toISOString(),
            content: lastContent,
            implemented_at_version: lastVersion,
            authors: [this.db.person.get(authorID)!],
          });
          revision_index++;
        }
      }

      for(const comment of sortedBy(jep.comments, c => c.id)) {
        const newID = this.generateNumID(this.db.comment.size);
        const author = this.db.person.get(this.personDetailsToID.get(`${openjdkDomain} username ${comment.author.username}`)!)!;
        this.db.comment.set(newID, {
          comment_id: newID,
          author_id: author.person_id,
          author,
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          created_at: comment.created.toISOString(),
          content: comment.content,
        });
      }
    }
    // one more run to get related proposals
    for(const jep of jeps) {
      const proposal = this.db.proposal.get(`${this.projID()} ${jep.id}`)!;
      for(const relatedID of jep.relatedIDs) if(relatedID !== jep.supersederID) {
        const rel_proposal = this.db.proposal.get(`${this.projID()} ${relatedID}`)!;
        this.db.related_proposal.set(`${proposal.project_id} ${proposal.proposal_id} ${rel_proposal.project_id} ${rel_proposal.proposal_id}`, new RelatedProposalSQL({
          proposal: proposal,
          related_proposal: rel_proposal,
          type: 'related',
        }));
      }
      if(jep.supersederID !== undefined) {
        const sup_proposal = this.db.proposal.get(`${this.projID()} ${jep.supersederID}`)!;
        this.db.related_proposal.set(`${sup_proposal.project_id} ${sup_proposal.proposal_id} ${proposal.project_id} ${proposal.proposal_id}`, new RelatedProposalSQL({
          proposal: sup_proposal,
          related_proposal: proposal,
          type: 'supersedes',
        }));
      }
    }
  }
}