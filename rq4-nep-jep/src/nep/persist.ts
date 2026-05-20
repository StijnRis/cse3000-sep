import { Persister, realNameDomain, emailDomain, githubDomain, RelatedProposalSQL } from "../persist.ts";
import type { NEP, NEPRevision } from "./scraper.ts";
import { isGithubComment } from "./scraper.ts";
import { sortedBy } from "../util.ts";

const numpyDomain = 'numpy.org';

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
      'Active': 'accepted',
      'Accepted': 'accepted',
      'Deferred': 'review',
      'Rejected': 'rejected',
      'Withdrawn': 'withdrawn',
      'Final': 'accepted',
      'Superseded': 'superseded',
      'Provisional': 'accepted',
    } as const)[status] : 'unknown';
  }

  persistPeople(neps: NEP[]) {
    const numpyOrgID = this.generateNumID(0);
    const numpyOrg = {
      organisation_id: numpyOrgID,
      organisation_name: 'NumPy',
    };
    this.db.organisation.set(numpyOrgID, numpyOrg);

    for(const nep of sortedBy(neps, n => n.id)) {
      for(const rev of sortedBy(nep.revisions, r => r.created.getDate()))
        for(const author of sortedBy(rev.authors, a => a.name)) {
          let person_id;
          if(author.email !== undefined) {
            person_id = this.ensurePerson(numpyDomain, 'email', author.email, author.name, [numpyOrg]).person_id;
            this.addPersonDetails(person_id, numpyDomain, 'email', author.email);
          } else {
            person_id = this.ensurePerson(numpyDomain, 'display_name', author.name, author.name, [numpyOrg]).person_id;
          }
          this.addPersonDetails(person_id, numpyDomain, 'display_name', author.name);
        }
      for(const comment of sortedBy(nep.comments, c => c.id)) {
        if(isGithubComment(comment)) {
          const {person_id} = this.ensurePerson(githubDomain, 'username', comment.author.login, comment.author.name); // github commenters may not be affiliated with numpy I guess
          this.addPersonDetails(person_id, githubDomain, 'username', comment.author.login);
          this.addPersonDetails(person_id, githubDomain, 'display_name', comment.author.name);
        } else {
          const {person_id} = this.ensurePerson(emailDomain, 'email', comment.author.email, comment.author.name, [numpyOrg]);
          this.addPersonDetails(person_id, emailDomain, 'email', comment.author.email);
          this.addPersonDetails(person_id, emailDomain, 'display_name', comment.author.name);
        }
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

      let status_index = 0; // increment only when status actually changes from one revision to the next
      nep.revisions
        // index ordered by creation date ascending
        .toSorted(({created: d1}, {created: d2}) => d1.getTime() - d2.getTime())
        .forEach((rev, i, revs) => {
          const revisionKey = `${this.projID()} ${id} ${i}`;
          const allAuthorIDs = rev.authors
            .map(author => this.personDetailsToID.get(author.email === undefined
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
            this.db.proposal_status.set(revisionKey, {
              project_id: proposal.project_id,
              proposal_id: proposal.proposal_id,
              proposal,
              status_index,
              normalised_status: this.normalizeStatus(rev.status),
              raw_status: rev.status,
              created_at: rev.created.toISOString(),
            });
            status_index++;
          }
        });

      for(const comment of nep.comments) {
        const author = this.db.person.get(
          this.personDetailsToID.get(isGithubComment(comment)
            ? `${githubDomain} username ${comment.author.login}`
            : `${emailDomain} email ${comment.author.email}`)!
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