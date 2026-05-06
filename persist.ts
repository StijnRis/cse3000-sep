import { JEP } from "./jep-scraper.ts";
import { isGithubComment, NEP } from "./nep-scraper.ts";
import type { CommentSQL, PersonSQL, PersonUsernameSQL, ProjectSQL, ProposalRevisionAuthorSQL, ProposalRevisionSQL, ProposalSQL } from "./types.d.ts";

export class Database {
  project = new Map<number/*project_id*/, ProjectSQL>();
  person = new Map<number/*person_id*/, PersonSQL>();
  person_username = new Map<[number/*person_id*/, string/*domain*/, string/*username*/], PersonUsernameSQL>();
  proposal = new Map<[number/*project_id*/, string/*proposal_id*/], ProposalSQL>();
  proposal_revision = new Map<[number/*project_id*/, string/*proposal_id*/, number/*revision_index*/], ProposalRevisionSQL>();
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
  persistPeople(jeps: JEP[]) {
    for(const jep of jeps) {
      if(jep.owner) this.addPerson(jep.owner.username, jep.owner.name, openjdkDomain);
      for(const author of jep.authors) this.addPerson(author, author, realNameDomain); //authors are only listed by name
      for(const comment of jep.comments) this.addPerson(comment.author.username, comment.author.name, openjdkDomain);
    }
  }
  persistSEPs(jeps: JEP[]) {
    for(const jep of jeps) {
      const id = `${jep.id}`;
      this.db.proposal.set([this.projID(), id], {
        project_id: this.projID(),
        proposal_id: id,
        proposer_id: jep.owner ? this.usernameToID.get([openjdkDomain, jep.owner.username]) : undefined,
        proposal_type: jep.type,
      });
      this.db.proposal_revision.set([this.projID(), id, 0], {
        project_id: this.projID(),
        proposal_id: id,
        revision_index: 0,
        title: jep.title,
        created_at: jep.created,
        content: jep.content,
      });
      for(const author of jep.authors) {
        this.db.proposal_revision_author.push({
          project_id: this.projID(),
          proposal_id: id,
          revision_index: 0,
          author_id: this.usernameToID.get([realNameDomain, author])!,
        });
      }
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
    }
  }
}

export class NEPPersister extends Persister<NEP> {
  project = {
    project_id: 2,
    project_name: 'NumPy',
    enhancement_proposal_name: 'NEP',
    copyright: 'GPLv2',
  };
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
    for(const nep of neps) {
      const id = `${nep.id}`;
      this.db.proposal.set([this.projID(), id], {
        project_id: this.projID(),
        proposal_id: id,
        proposal_type: nep.type,
      });
      this.db.proposal_revision.set([this.projID(), id, 0], {
        project_id: this.projID(),
        proposal_id: id,
        revision_index: 0,
        title: nep.title,
        created_at: nep.created,
        content: nep.content,
      });
      for(const author of nep.authors) {

      }
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
    }
  }
}
