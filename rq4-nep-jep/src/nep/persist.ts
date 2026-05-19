import { Persister, realNameDomain, emailDomain, githubDomain, RelatedProposalSQL } from "../persist.ts";
import type { NEP, NEPRevision } from "./scraper.ts";
import { isGithubComment } from "./scraper.ts";
import { sortedBy } from "../util.ts";

export class NEPPersister extends Persister<NEP> {
  project = {
    project_id: 2,
    project_name: 'NumPy',
    enhancement_proposal_name: 'NEP',
    copyright: 'Public Domain',
  };

  normalizeStatus(status: NEPRevision['status']) {
    return status ? ({
      'Draft': 'review',
      'Active': 'active',
      'Accepted': 'accepted',
      'Deferred': 'withdrawn',
      'Rejected': 'rejected',
      'Withdrawn': 'withdrawn',
      'Final': 'accepted',
      'Superseded': 'superseded',
      'Provisional': 'provisional',
    } as const)[status] : 'unknown';
  }

  persistPeople(neps: NEP[]) {
    for(const nep of sortedBy(neps, n => n.id)) {
      for(const rev of sortedBy(nep.revisions, r => r.created.getDate()))
        for(const author of sortedBy(rev.authors, a => a.name)) {
          if(author.email === undefined) this.addPerson(author.name, author.name, realNameDomain);
          else this.addPerson(author.email, author.name, emailDomain);
        }
      for(const comment of sortedBy(nep.comments, c => c.id)) {
        if(isGithubComment(comment)) this.addPerson(comment.author.login, comment.author.name, githubDomain);
        else this.addPerson(comment.author.email, comment.author.name, emailDomain);
      }
    }
  }
  persistSEPs(neps: NEP[]) {
    // first need to generate IDs so that they can be referenced by replies
    const commentToID = new Map<number | string, number>();
    for(const nep of neps) {
      for(const comm of sortedBy(nep.comments, c => c.id)) {
        if(!commentToID.has(comm.id)) {
          const newID = this.generateNumID(commentToID.size);
          commentToID.set(comm.id, newID);
        }
      }
    }

    // const byFinal = groupByFinalID(neps);
    for(const nep of neps) {
      const id = `${nep.id}`;
      const proposal = {
        project_id: this.projID(),
        project: this.project,
        proposal_id: id,
        proposal_type: nep.type,
      };
      this.db.proposal.set(`${this.projID()} ${id}`, proposal);

      let stage_index = 0; // increment only when status actually changes from one revision to the next
      nep.revisions
        // index ordered by creation date ascending
        .toSorted(({created: d1}, {created: d2}) => d1.getTime() - d2.getTime())
        .forEach((rev, i, revs) => {
          const revisionKey = `${this.projID()} ${id} ${i}`;
          const allAuthorIDs = rev.authors
            .map(author => this.usernameToID.get(author.email === undefined
              ? `${realNameDomain} ${author.name}`
              : `${emailDomain} ${author.email}`)!);
          this.db.proposal_revision.set(revisionKey, {
            project_id: proposal.project_id,
            proposal_id: proposal.proposal_id,
            proposal,
            revision_index: i,
            title: nep.title,
            created_at: rev.created.toISOString(),
            content: rev.content,
            authors: allAuthorIDs.map(id => this.db.person.get(id)!),
          });
          // for(const author of nep.authors) {
          //   this.db.proposal_revision_author.push({
          //     project_id: this.projID(),
          //     proposal_id: id,
          //     revision_index: idx,
          //     author_id: ,
          //   });
          // }
          if(i === 0 || rev.status !== revs[i-1].status) {
            this.db.stage_history.set(revisionKey, {
              project_id: proposal.project_id,
              proposal_id: proposal.proposal_id,
              proposal,
              stage_index,
              normalised_status: this.normalizeStatus(rev.status),
              raw_status: rev.status,
              created_at: rev.created.toISOString(),
            });
            stage_index++;
          }
        });

      for(const comment of nep.comments) {
        const author = this.db.person.get(
          this.usernameToID.get(isGithubComment(comment)
            ? `${githubDomain} ${comment.author.login}`
            : `${emailDomain} ${comment.author.email}`)!
        )!;
        const newID = commentToID.get(comment.id)!;
        this.db.comment.set(newID, {
          comment_id: newID,
          author_id: author.person_id,
          author,
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          created_at: comment.created.toISOString(),
          comment_on_comment_id: comment.replyTo !== undefined
            ? commentToID.get(comment.replyTo)
            : undefined,
          content: comment.body,
        });
      }
    }

    // another run to find related proposals
    for(const nep of neps) {
      if(nep.supersederID !== undefined) {
        const superseded = this.db.proposal.get(`${this.projID()} ${nep.id}`)!;
        const superseder = this.db.proposal.get(`${this.projID()} ${nep.supersederID}`)!;
        this.db.related_proposal.push(new RelatedProposalSQL({
          proposal: superseder,
          related_proposal: superseded,
          type: 'supersedes',
        }));
      }
    }
  }
}