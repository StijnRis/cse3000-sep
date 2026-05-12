import { groupByFinalID } from "./common.ts";
import type { JEP } from "./jep-scraper.ts";
import { isGithubComment } from "./nep-scraper.ts";
import type { NEP } from "./nep-scraper.ts";
import { Column, DataSource, Entity, JoinColumn, JoinTable, ManyToMany, ManyToOne, PrimaryColumn } from 'typeorm';
import 'reflect-metadata';

@Entity('Project')
export class ProjectSQL {
  @PrimaryColumn({type: 'integer'})
  project_id: number;
  @Column({type: 'text'})
  project_name: string;
  @Column({type: 'text'})
  enhancement_proposal_name: string;
  @Column({type: 'text'})
  copyright: string;
};

@Entity('Person')
class PersonSQL {
  @PrimaryColumn({type: 'integer'})
  person_id: number;
  @Column({type: 'text'})
  full_name: string;
};

@Entity('PersonUsername')
class PersonUsernameSQL {
  @PrimaryColumn({type: 'integer'})
  person_id: number;
  @ManyToOne(() => PersonSQL) @JoinColumn({name: 'person_id', referencedColumnName: 'person_id'})
  person: PersonSQL; //fk
  @PrimaryColumn({type: 'text'})
  domain: string;
  @PrimaryColumn({type: 'text'})
  username: string;
};

@Entity('Proposal')
class ProposalSQL {
  @PrimaryColumn({type: 'integer'})
  project_id: number;
  @ManyToOne(() => ProjectSQL) @JoinColumn({name: 'project_id', referencedColumnName: 'project_id'})
  project: ProjectSQL; //fk
  @PrimaryColumn({type: 'text'})
  proposal_id: string;
  @Column({type: 'text', nullable: true})
  topic?: string;
  @Column({type: 'text', nullable: true})
  proposal_type?: string;
};

@Entity('ProposalRevision')
class ProposalRevisionSQL {
  @PrimaryColumn({type: 'integer'})
  project_id: number;
  @PrimaryColumn({type: 'text'})
  proposal_id: string;
  @ManyToOne(() => ProposalSQL) @JoinColumn([
    {name: 'project_id', referencedColumnName: 'project_id'},
    {name: 'proposal_id', referencedColumnName: 'proposal_id'},
  ])
  proposal: ProposalSQL; //fk'
  @PrimaryColumn({type: 'integer'})
  revision_index: number;
  @Column({type: 'text'})
  title: string;
  @Column({type: 'datetime'})
  created_at: string; //date
  @Column({type: 'text'})
  content: string;
  @Column({type: 'text', nullable: true})
  implemented_at_version?: string;
  @ManyToMany(() => PersonSQL) @JoinTable({
    name: 'ProposalRevisionAuthor',
    joinColumns: [
      {name: 'project_id', referencedColumnName: 'project_id'},
      {name: 'proposal_id', referencedColumnName: 'proposal_id'},
      {name: 'revision_index', referencedColumnName: 'revision_index'},
    ],
    inverseJoinColumns: [{name: 'author_id', referencedColumnName: 'person_id'}],
  })
  authors: PersonSQL[];
};

@Entity('StageHistory')
class StageHistorySQL {
  @PrimaryColumn({type: 'integer'})
  project_id: number;
  @PrimaryColumn({type: 'text'})
  proposal_id: string;
  @ManyToOne(() => ProposalSQL) @JoinColumn([
    {name: 'project_id', referencedColumnName: 'project_id'},
    {name: 'proposal_id', referencedColumnName: 'proposal_id'},
  ])
  proposal: ProposalSQL; //fk'
  @PrimaryColumn({type: 'integer'})
  stage_index: number;
  @Column({type: 'text'})
  normalised_status: 'accepted' | 'rejected' | 'draft' | 'review' | 'withdrawn' | 'superseded' | 'active' | 'unknown';
  @Column({type: 'text', nullable: true})
  raw_status?: string;
  @Column({type: 'datetime'})
  created_at: string; //date
};

// class ProposalRevisionAuthorSQL { //many to many
//   project_id: number; //fk'
//   proposal_id: string; //fk'
//   revision_index: number; //fk'
//   author_id: number; //fk
// };

@Entity('Comment')
class CommentSQL {
  @PrimaryColumn({type: 'integer'})
  comment_id: number;
  @Column({type: 'integer'})
  author_id: number;
  @ManyToOne(() => PersonSQL) @JoinColumn({name: 'author_id', referencedColumnName: 'person_id'})
  author: PersonSQL; //fk
  @Column({type: 'integer'})
  project_id: number;
  @Column({type: 'text'})
  proposal_id: string;
  @ManyToOne(() => ProposalSQL) @JoinColumn([
    {name: 'project_id', referencedColumnName: 'project_id'},
    {name: 'proposal_id', referencedColumnName: 'proposal_id'},
  ])
  proposal: ProposalSQL; //fk'
  @Column({type: 'integer', nullable: true}) //@ForeignKey(() => CommentSQL, {deferrable: 'INITIALLY DEFERRED'}) @JoinColumn({name: 'comment_on_comment_id'})
  comment_on_comment_id?: number; //fk''
  @Column({type: 'datetime'})
  created_at: string; //date
  @Column({type: 'text'})
  content: string;
};

class Database {
  project = new Map<number/*project_id*/, ProjectSQL>();
  person = new Map<number/*person_id*/, PersonSQL>();
  // combining values into string as a hack, because arrays don't have proper equals functionality
  person_username = new Map<string/*person_id domain username*/, PersonUsernameSQL>();
  proposal = new Map<string/*project_id proposal_id*/, ProposalSQL>();
  proposal_revision = new Map<string/*project_id proposal_id revision_index*/, ProposalRevisionSQL>();
  stage_history = new Map<string/*project_id proposal_id stage_index*/, StageHistorySQL>();
  // proposal_revision_author: ProposalRevisionAuthorSQL[] = [];
  comment = new Map<number/*comment_id*/, CommentSQL>();
}

const realNameDomain = 'real_name';
const emailDomain = 'email';
const githubDomain = 'github.com';
const openjdkDomain = 'openjdk.org';

abstract class Persister<SEP_T> {
  abstract project: ProjectSQL;

  db = new Database();
  prepareDB() {
    this.db.project.set(this.projID(), this.project);
  }

  projID() {return this.project.project_id;}
  /**
   * According to agreed strategy of unique ID generation
   * @param id in-project (unique) ID
   * @returns universally unique ID
   */
  generateNumID(id: number) {return this.projID() * 1000000 + id;}

  usernameToID = new Map<string/*domain username*/, number>();
  addPerson(username: string, name: string, domain: string) {
    const key = `${domain} ${username}`;
    if(this.usernameToID.has(key)) return;
    const newID = this.generateNumID(this.usernameToID.size);
    this.usernameToID.set(key, newID);
    const person: PersonSQL = {
      person_id: newID,
      full_name: name,
    };
    this.db.person.set(newID, person);
    this.db.person_username.set(`${newID} ${domain} ${username}`, {
      person_id: person.person_id,
      person,
      domain,
      username,
    });
  }

  /**
   * Implementations should come up with their own translation schemes
   * @param status raw status
   */
  abstract normalizeStatus(status: string | undefined): StageHistorySQL['normalised_status'];

  abstract persistPeople(seps: SEP_T[]): void;
  abstract persistSEPs(seps: SEP_T[]): void;

  /**
   * Persists a collection of SEPs into an intermediate memory-based database
   * @param seps collection of SEPs
   * @returns in-memory database, must then be dumped into real DB
   */
  persist(seps: SEP_T[]): Database {
    this.prepareDB();
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
      const proposal: ProposalSQL = {
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
        this.db.stage_history.set(revisionKey, {
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          stage_index: idx,
          normalised_status: this.normalizeStatus(jep.status),
          raw_status: jep.status,
          created_at: jep.created.toISOString(),
        });
        for(const comment of jep.comments) {
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
    // first need to generate IDs so that they can be referenced by replies
    const commentToID = new Map<number | string, number>();
    for(const nep of neps) {
      for(const comm of nep.comments) {
        if(!commentToID.has(comm.id)) {
          const newID = this.generateNumID(commentToID.size);
          commentToID.set(comm.id, newID);
        }
      }
    }
    const byFinal = groupByFinalID(neps);
    for(const [fid, group] of byFinal) {
      const id = `${fid}`;
      const final = group[group.length - 1];
      const proposal: ProposalSQL = {
        project_id: this.projID(),
        project: this.project,
        proposal_id: id,
        proposal_type: final.type,
      };
      this.db.proposal.set(`${this.projID()} ${id}`, proposal);
      group.forEach((nep, idx) => {
        const revisionKey = `${this.projID()} ${id} ${idx}`;
        const allAuthorIDs = nep.authors
          .map(author => this.usernameToID.get(author.email === undefined
            ? `${realNameDomain} ${author.name}`
            : `${emailDomain} ${author.email}`)!);
        this.db.proposal_revision.set(revisionKey, {
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          revision_index: idx,
          title: nep.title,
          created_at: nep.created.toISOString(),
          content: nep.content,
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
        this.db.stage_history.set(revisionKey, {
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          stage_index: idx,
          normalised_status: this.normalizeStatus(nep.status),
          raw_status: nep.status,
          created_at: nep.created.toISOString(),
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
      });
    }
  }
}

/**
 * Creates and initializes new SQLite data source
 * @param database file name, or in-memory
 * @returns data source
 */
export const dataSource = async (database: string): Promise<DataSource> => {
  const db = new DataSource({
    type: 'sqlite',
    database,
    entities: [ProjectSQL, PersonSQL, PersonUsernameSQL, ProposalSQL, ProposalRevisionSQL, StageHistorySQL, CommentSQL],
    synchronize: true,
    // logging: true,
  });
  await db.initialize();
  return db;
};

/**
 * Dumps in-memory DB into real DB
 * @param dataSource real DB
 * @param db in-memory DB
 */
export const dumpDatabase = async (dataSource: DataSource, db: Database) => {
  await dataSource.getRepository(ProjectSQL).save(db.project.values().toArray());
  await dataSource.getRepository(PersonSQL).save(db.person.values().toArray());
  await dataSource.getRepository(PersonUsernameSQL).save(db.person_username.values().toArray());
  await dataSource.getRepository(ProposalSQL).save(db.proposal.values().toArray());
  await dataSource.getRepository(ProposalRevisionSQL).save(db.proposal_revision.values().toArray());
  await dataSource.getRepository(StageHistorySQL).save(db.stage_history.values().toArray());
  await dataSource.getRepository(CommentSQL).save(db.comment.values().toArray());
};
