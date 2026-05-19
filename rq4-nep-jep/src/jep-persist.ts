import { Persister, realNameDomain } from "./persist.ts"; 
import type { JEP } from "./jep-scraper.ts";
import { groupByFinalID } from "./common.ts";
import { sortedBy } from "./util.ts";

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
      'Withdrawn': 'withdrawn',
      'Rejected': 'rejected',
      'Active': 'active',
      'Closed / Delivered': 'accepted',
      'Closed / Withdrawn': 'withdrawn',
      'Proposed to Target': 'accepted',
      'Integrated': 'accepted',
      'Targeted': 'review',
    } as const)[status] || 'unknown') : 'unknown';
  }

  persistPeople(jeps: JEP[]) {
    for(const jep of sortedBy(jeps, j => j.id)) {
      if(jep.owner) this.addPerson(jep.owner.username, jep.owner.name, openjdkDomain);
      for(const author of sortedBy(jep.authors, a => a))
        this.addPerson(author, author, realNameDomain); //authors are only listed by name
      for(const comment of sortedBy(jep.comments, c => c.id))
        this.addPerson(comment.author.username, comment.author.name, openjdkDomain);
    }
  }
  persistSEPs(jeps: JEP[]) {
    const byFinal = groupByFinalID(jeps);
    for(const [fid, group] of byFinal) {
      const id = `${fid}`;
      const final = group[group.length - 1];
      const proposal = {
        project_id: this.projID(),
        project: this.project,
        proposal_id: id,
        // proposer_id: final.owner ? this.usernameToID.get([openjdkDomain, final.owner.username]) : undefined,
        proposal_type: final.type,
      };
      this.db.proposal.set(`${this.projID()} ${id}`, proposal);
      group.forEach((jep, idx) => {
        const revisionKey = `${this.projID()} ${id} ${idx}`;
        const allAuthorIDs = new Set(jep.authors
            .map(name => this.usernameToID.get(`${realNameDomain} ${name}`)!));
        this.db.proposal_revision.set(revisionKey, {
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          revision_index: idx,
          title: jep.title,
          created_at: jep.created.toISOString(),
          content: jep.content,
          implemented_at_version: jep.release,
          authors: allAuthorIDs.keys().toArray().map(id => this.db.person.get(id)!),
        });
        // for(const author of jep.authors) {
        //   this.db.proposal_revision_author.push({
        //     project_id: this.projID(),
        //     proposal_id: id,
        //     revision_index: idx,
        //     author_id: this.usernameToID.get([realNameDomain, author])!,
        //   });
        // }
        // TODO rework JEP stage history logic
        this.db.stage_history.set(revisionKey, {
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          stage_index: idx,
          normalised_status: this.normalizeStatus(jep.status),
          raw_status: jep.status,
          created_at: jep.created.toISOString(),
        });
        for(const comment of sortedBy(jep.comments, c => c.id)) {
          const newID = this.generateNumID(this.db.comment.size);
          const author = this.db.person.get(this.usernameToID.get(`${openjdkDomain} ${comment.author.username}`)!)!;
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
      });
    }
  }
}