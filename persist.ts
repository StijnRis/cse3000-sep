import { existsSync, unlinkSync } from 'node:fs';
import { groupByFinalID } from "./common.ts";
import type { JEP } from "./jep-scraper.ts";
import { isGithubComment } from "./nep-scraper.ts";
import type { NEP } from "./nep-scraper.ts";

type ProjectSQL = {
  project_id: number,
  project_name: string,
  enhancement_proposal_name: string,
  copyright: string,
};

type PersonSQL = {
  person_id: number,
};

type PersonUsernameSQL = {
  person_id: number, //fk
  domain: string,
  username: string,
  real_name: string,
};

type ProposalSQL = {
  project_id: number, //fk
  proposal_id: string,
  proposer_id?: number, //fk'
  topic?: string,
  proposal_type?: string,
};

type ProposalRevisionSQL = {
  project_id: number, //fk'
  proposal_id: string, //fk'
  revision_index: number,
  title: string,
  created_at: Date,
  content: string,
  implemented_at_version?: string,
};

type StageHistorySQL = {
  project_id: number, //fk'
  proposal_id: string, //fk'
  stage_index: number,
  normalised_status: 'accepted' | 'rejected' | 'draft' | 'review' | 'withdrawn' | 'superseded' | 'active' | 'unknown',
  raw_status?: string,
  created_at: Date,
};

type ProposalRevisionAuthorSQL = { //many to many
  project_id: number, //fk'
  proposal_id: string, //fk'
  revision_index: number, //fk'
  author_id: number, //fk
};

type CommentSQL = {
  comment_id: number,
  author_id: number, //fk
  project_id: number, //fk'
  proposal_id: string, //fk'
  comment_on_comment_id?: number, //fk''
  created_at: Date,
  content: string,
};

class Database {
  project = new Map<number/*project_id*/, ProjectSQL>();
  person = new Map<number/*person_id*/, PersonSQL>();
  person_username = new Map<[number/*person_id*/, string/*domain*/, string/*username*/], PersonUsernameSQL>();
  proposal = new Map<[number/*project_id*/, string/*proposal_id*/], ProposalSQL>();
  proposal_revision = new Map<[number/*project_id*/, string/*proposal_id*/, number/*revision_index*/], ProposalRevisionSQL>();
  stage_history = new Map<[number/*project_id*/, string/*proposal_id*/, number/*stage_index*/], StageHistorySQL>();
  proposal_revision_author: ProposalRevisionAuthorSQL[] = [];
  comment = new Map<[number/*project_id*/, string/*proposal_id*/, number/*comment_id*/], CommentSQL>();
}

const realNameDomain = 'real_name';
const emailDomain = 'email';
const githubDomain = 'github.com';
const openjdkDomain = 'openjdk.org';

abstract class Persister<SEP_T> {
  abstract project: ProjectSQL;
  db = new Database();
  projID() {return this.project.project_id;}
  prepareDB() {
    this.db.project.set(this.projID(), this.project);
  };
  usernameToID = new Map<[string/*domain*/, string/*username*/], number>();
  addPerson(username: string, name: string, domain: string) {
    const newID = this.usernameToID.size;
    this.usernameToID.set([domain, username], newID);
    this.db.person.set(newID, {
      person_id: newID,
    });
    this.db.person_username.set([newID, domain, username], {
      person_id: newID,
      domain,
      username,
      real_name: name,
    });
  }
  abstract normalizeStatus(status: string | undefined): StageHistorySQL['normalised_status'];
  abstract persistPeople(seps: SEP_T[]): void;
  abstract persistSEPs(seps: SEP_T[]): void;
  persist(seps: SEP_T[]): Database {
    // first populate with all the people that can be found
    this.persistPeople(seps);
    // then fill SEPs
    this.persistSEPs(seps);
    return this.db;
  }
}

export class JEPPersister extends Persister<JEP> {
  project = {
    project_id: 6,
    project_name: 'OpenJDK',
    enhancement_proposal_name: 'JEP',
    copyright: 'GPLv2',
  };
  normalizeStatus(status: JEP['status']): StageHistorySQL['normalised_status'] {
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
    for(const jep of jeps) {
      if(jep.owner) this.addPerson(jep.owner.username, jep.owner.name, openjdkDomain);
      for(const author of jep.authors) this.addPerson(author, author, realNameDomain); //authors are only listed by name
      for(const comment of jep.comments) this.addPerson(comment.author.username, comment.author.name, openjdkDomain);
    }
  }
  persistSEPs(jeps: JEP[]) {
    const byFinal = groupByFinalID(jeps);
    for(const [fid, group] of byFinal) {
      const id = `${fid}`;
      const final = group[group.length - 1];
      this.db.proposal.set([this.projID(), id], {
        project_id: this.projID(),
        proposal_id: id,
        proposer_id: final.owner ? this.usernameToID.get([openjdkDomain, final.owner.username]) : undefined,
        proposal_type: final.type,
      });
      group.forEach((jep, idx) => {
        this.db.proposal_revision.set([this.projID(), id, idx], {
          project_id: this.projID(),
          proposal_id: id,
          revision_index: idx,
          title: jep.title,
          created_at: jep.created,
          content: jep.content,
        });
        for(const author of jep.authors) {
          this.db.proposal_revision_author.push({
            project_id: this.projID(),
            proposal_id: id,
            revision_index: idx,
            author_id: this.usernameToID.get([realNameDomain, author])!,
          });
        }
        this.db.stage_history.set([this.projID(), id, idx], {
          project_id: this.projID(),
          proposal_id: id,
          stage_index: idx,
          normalised_status: this.normalizeStatus(jep.status),
          raw_status: jep.status,
          created_at: jep.created,
        });
        for(const comment of jep.comments) {
          this.db.comment.set([this.projID(), id, comment.id], {
            project_id: this.projID(),
            comment_id: comment.id,
            author_id: this.usernameToID.get([openjdkDomain, comment.author.username])!,
            proposal_id: id,
            created_at: comment.created,
            content: comment.content,
          });
        }
      });
    }
  }
}

export class NEPPersister extends Persister<NEP> {
  project = {
    project_id: 2,
    project_name: 'NumPy',
    enhancement_proposal_name: 'NEP',
    copyright: 'Public Domain',
  };
  normalizeStatus(status: NEP['status']): StageHistorySQL["normalised_status"] {
    return ({
      'Draft': 'review',
      'Active': 'active',
      'Accepted': 'accepted',
      'Deferred': 'withdrawn',
      'Rejected': 'rejected',
      'Withdrawn': 'withdrawn',
      'Final': 'accepted',
      'Superseded': 'superseded',
    } as const)[status] || 'unknown';
  }
  persistPeople(neps: NEP[]) {
    for(const nep of neps) {
      for(const author of nep.authors) {
        if(author.email === undefined) this.addPerson(author.name, author.name, realNameDomain);
        else this.addPerson(author.email, author.name, emailDomain);
      }
      for(const comment of nep.comments) {
        if(isGithubComment(comment)) this.addPerson(comment.author.login, comment.author.name, githubDomain);
        else this.addPerson(comment.author.email, comment.author.name, emailDomain);
      }
    }
  }
  persistSEPs(neps: NEP[]) {
    const byFinal = groupByFinalID(neps);
    for(const [fid, group] of byFinal) {
      const id = `${fid}`;
      const final = group[group.length - 1];
      this.db.proposal.set([this.projID(), id], {
        project_id: this.projID(),
        proposal_id: id,
        proposal_type: final.type,
      });
      group.forEach((nep, idx) => {
        this.db.proposal_revision.set([this.projID(), id, idx], {
          project_id: this.projID(),
          proposal_id: id,
          revision_index: idx,
          title: nep.title,
          created_at: nep.created,
          content: nep.content,
        });
        for(const author of nep.authors) {
          this.db.proposal_revision_author.push({
            project_id: this.projID(),
            proposal_id: id,
            revision_index: idx,
            author_id: this.usernameToID.get(author.email === undefined
              ? [realNameDomain, author.name]
              : [emailDomain, author.email])!,
          });
        }
        this.db.stage_history.set([this.projID(), id, idx], {
          project_id: this.projID(),
          proposal_id: id,
          stage_index: idx,
          normalised_status: this.normalizeStatus(nep.status),
          raw_status: nep.status,
          created_at: nep.created,
        });
        nep.comments.forEach((comment, i) => {
          this.db.comment.set([this.projID(), id, i], {
            project_id: this.projID(),
            comment_id: isGithubComment(comment) ? comment.id : i,
            author_id: this.usernameToID.get(isGithubComment(comment)
              ? [githubDomain, comment.author.login]
              : [emailDomain, comment.author.email])!,
            proposal_id: id,
            created_at: comment.created,
            comment_on_comment_id: isGithubComment(comment) ? comment.reply_to : undefined,
            content: comment.body,
          });
        });
      });
    }
  }
}
