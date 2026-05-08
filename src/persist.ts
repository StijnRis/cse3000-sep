import { groupByFinalID } from "./common.ts";
import type { JEP } from "./jep-scraper.ts";
import { isGithubComment } from "./nep-scraper.ts";
import type { NEP } from "./nep-scraper.ts";
import { Column, DataSource, Entity, JoinColumn, ManyToMany, ManyToOne, PrimaryColumn } from 'typeorm';
import 'reflect-metadata';

@Entity('Project')
export class ProjectSQL {
  @PrimaryColumn()
  project_id: number;
  @Column()
  project_name: string;
  @Column()
  enhancement_proposal_name: string;
  @Column()
  copyright: string;
};

@Entity('Person')
class PersonSQL {
  @PrimaryColumn()
  person_id: number;
  @Column()
  full_name: string;
};

@Entity('PersonUsername')
class PersonUsernameSQL {
  @PrimaryColumn()
  person_id: number;
  @ManyToOne(() => PersonSQL) @JoinColumn({name: 'person_id', referencedColumnName: 'person_id'})
  person: PersonSQL; //fk
  @PrimaryColumn()
  domain: string;
  @PrimaryColumn()
  username: string;
};

@Entity('Proposal')
class ProposalSQL {
  @PrimaryColumn()
  project_id: number;
  @ManyToOne(() => ProjectSQL) @JoinColumn({name: 'project_id', referencedColumnName: 'project_id'})
  project: ProjectSQL; //fk
  @PrimaryColumn()
  proposal_id: string;
  @Column({nullable: true})
  topic?: string;
  @Column({nullable: true})
  proposal_type?: string;
};

@Entity('ProposalRevision')
class ProposalRevisionSQL {
  @PrimaryColumn()
  project_id: number;
  @PrimaryColumn()
  proposal_id: string;
  @ManyToOne(() => ProposalSQL) @JoinColumn([
    {name: 'project_id', referencedColumnName: 'project_id'},
    {name: 'proposal_id', referencedColumnName: 'proposal_id'},
  ])
  proposal: ProposalSQL; //fk'
  @PrimaryColumn()
  revision_index: number;
  @Column()
  title: string;
  @Column()
  created_at: Date;
  @Column()
  content: string;
  @Column({nullable: true})
  implemented_at_version?: string;
  @ManyToMany(() => PersonSQL)
  authors: PersonSQL[];
};

@Entity('StageHistory')
class StageHistorySQL {
  @PrimaryColumn()
  project_id: number;
  @PrimaryColumn()
  proposal_id: string;
  @ManyToOne(() => ProposalSQL) @JoinColumn([
    {name: 'project_id', referencedColumnName: 'project_id'},
    {name: 'proposal_id', referencedColumnName: 'proposal_id'},
  ])
  proposal: ProposalSQL; //fk'
  @PrimaryColumn()
  stage_index: number;
  @Column()
  normalised_status: 'accepted' | 'rejected' | 'draft' | 'review' | 'withdrawn' | 'superseded' | 'active' | 'unknown';
  @Column({nullable: true})
  raw_status?: string;
  @Column()
  created_at: Date;
};

// class ProposalRevisionAuthorSQL { //many to many
//   project_id: number; //fk'
//   proposal_id: string; //fk'
//   revision_index: number; //fk'
//   author_id: number; //fk
// };

@Entity('Comment')
class CommentSQL {
  @PrimaryColumn()
  comment_id: number;
  @Column()
  author_id: number;
  @ManyToOne(() => PersonSQL) @JoinColumn({name: 'author_id', referencedColumnName: 'person_id'})
  author: PersonSQL; //fk
  @Column()
  project_id: number;
  @Column()
  proposal_id: string;
  @ManyToOne(() => ProposalSQL) @JoinColumn([
    {name: 'project_id', referencedColumnName: 'project_id'},
    {name: 'proposal_id', referencedColumnName: 'proposal_id'},
  ])
  proposal: ProposalSQL; //fk'
  @Column({nullable: true}) //@ForeignKey(() => CommentSQL, {deferrable: 'INITIALLY DEFERRED'}) @JoinColumn({name: 'comment_on_comment_id'})
  comment_on_comment_id?: number; //fk''
  @Column()
  created_at: Date;
  @Column()
  content: string;
};

class Database {
  project = new Map<number/*project_id*/, ProjectSQL>();
  person = new Map<number/*person_id*/, PersonSQL>();
  person_username = new Map<[number/*person_id*/, string/*domain*/, string/*username*/], PersonUsernameSQL>();
  proposal = new Map<[number/*project_id*/, string/*proposal_id*/], ProposalSQL>();
  proposal_revision = new Map<[number/*project_id*/, string/*proposal_id*/, number/*revision_index*/], ProposalRevisionSQL>();
  stage_history = new Map<[number/*project_id*/, string/*proposal_id*/, number/*stage_index*/], StageHistorySQL>();
  // proposal_revision_author: ProposalRevisionAuthorSQL[] = [];
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
    const person: PersonSQL = {
      person_id: newID,
      full_name: name,
    };
    this.db.person.set(newID, person);
    this.db.person_username.set([newID, domain, username], {
      person_id: person.person_id,
      person,
      domain,
      username,
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
      const proposal: ProposalSQL = {
        project_id: this.projID(),
        project: this.project,
        proposal_id: id,
        // proposer_id: final.owner ? this.usernameToID.get([openjdkDomain, final.owner.username]) : undefined,
        proposal_type: final.type,
      };
      this.db.proposal.set([this.projID(), id], proposal);
      group.forEach((jep, idx) => {
        const allAuthorIDs = new Set(jep.authors
            .map(name => this.usernameToID.get([realNameDomain, name])!));
        this.db.proposal_revision.set([this.projID(), id, idx], {
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          revision_index: idx,
          title: jep.title,
          created_at: jep.created,
          content: jep.content,
          implemented_at_version: jep.release?.toString(),
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
        this.db.stage_history.set([this.projID(), id, idx], {
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          stage_index: idx,
          normalised_status: this.normalizeStatus(jep.status),
          raw_status: jep.status,
          created_at: jep.created,
        });
        for(const comment of jep.comments) {
          const author = this.db.person.get(this.usernameToID.get([openjdkDomain, comment.author.username])!)!;
          this.db.comment.set([this.projID(), id, comment.id], {
            comment_id: comment.id,
            author_id: author.person_id,
            author,
            project_id: proposal.project_id,
            proposal_id: proposal.proposal_id,
            proposal,
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
      const proposal: ProposalSQL = {
        project_id: this.projID(),
        project: this.project,
        proposal_id: id,
        proposal_type: final.type,
      };
      this.db.proposal.set([this.projID(), id], proposal);
      group.forEach((nep, idx) => {
        const allAuthorIDs = nep.authors
          .map(author => this.usernameToID.get(author.email === undefined
            ? [realNameDomain, author.name]
            : [emailDomain, author.email])!);
        this.db.proposal_revision.set([this.projID(), id, idx], {
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          revision_index: idx,
          title: nep.title,
          created_at: nep.created,
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
        this.db.stage_history.set([this.projID(), id, idx], {
          project_id: proposal.project_id,
          proposal_id: proposal.proposal_id,
          proposal,
          stage_index: idx,
          normalised_status: this.normalizeStatus(nep.status),
          raw_status: nep.status,
          created_at: nep.created,
        });
        nep.comments.forEach((comment, i) => {
          const author = this.db.person.get(
            this.usernameToID.get(isGithubComment(comment)
              ? [githubDomain, comment.author.login]
              : [emailDomain, comment.author.email])!
          )!;
          this.db.comment.set([this.projID(), id, i], {
            comment_id: isGithubComment(comment) ? comment.id : i,
            author_id: author.person_id,
            author,
            project_id: proposal.project_id,
            proposal_id: proposal.proposal_id,
            proposal,
            created_at: comment.created,
            comment_on_comment_id: isGithubComment(comment) ? comment.reply_to : undefined,
            content: comment.body,
          });
        });
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
    logging: true,
  });
  await db.initialize();
  return db;
};

export const dumpDatabase = async (dataSource: DataSource, db: Database) => {
  await dataSource.getRepository(ProjectSQL).save(db.project.values().toArray());
  await dataSource.getRepository(PersonSQL).save(db.person.values().toArray());
  await dataSource.getRepository(PersonUsernameSQL).save(db.person_username.values().toArray());
  await dataSource.getRepository(ProposalSQL).save(db.proposal.values().toArray());
  await dataSource.getRepository(ProposalRevisionSQL).save(db.proposal_revision.values().toArray());
  await dataSource.getRepository(StageHistorySQL).save(db.stage_history.values().toArray());
  await dataSource.getRepository(CommentSQL).save(db.comment.values().toArray());
};
