import { groupByFinalID } from "./common.ts";
import type { JEP } from "./jep-scraper.ts";
import { isGithubComment } from "./nep-scraper.ts";
import type { NEP, NEPRevision } from "./nep-scraper.ts";
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
  normalised_status: 'accepted' | 'rejected' | 'draft' | 'review' | 'withdrawn' | 'superseded' | 'provisional' | 'active' | 'unknown';
  @Column({type: 'text', nullable: true})
  raw_status?: string;
  @Column({type: 'datetime'})
  created_at: string; //date
};

@Entity('RelatedProposal')
export class RelatedProposalSQL {
  @PrimaryColumn({type: 'integer'})
  project_id: number;
  @PrimaryColumn({type: 'text'})
  proposal_id: string;
  @ManyToOne(() => ProposalSQL) @JoinColumn([
    {name: 'project_id', referencedColumnName: 'project_id'},
    {name: 'proposal_id', referencedColumnName: 'proposal_id'},
  ])
  proposal: ProposalSQL;
  @PrimaryColumn({type: 'integer'})
  related_project_id: number;
  @PrimaryColumn({type: 'text'})
  related_proposal_id: string;
  @ManyToOne(() => ProposalSQL) @JoinColumn([
    {name: 'related_project_id', referencedColumnName: 'project_id'},
    {name: 'related_proposal_id', referencedColumnName: 'proposal_id'},
  ])
  related_proposal: ProposalSQL;
  @Column({type: 'text'})
  type: 'related' | 'supersedes';

  constructor(obj?: Pick<RelatedProposalSQL, 'proposal' | 'related_proposal' | 'type'>) {
    if(!obj) return; // i guess typeorm calls the constructor with no args
    this.project_id = obj.proposal.project_id;
    this.proposal_id = obj.proposal.proposal_id;
    this.proposal = obj.proposal;
    this.related_project_id = obj.related_proposal.project_id;
    this.related_proposal_id = obj.related_proposal.proposal_id;
    this.related_proposal = obj.related_proposal;
    this.type = obj.type;
  }
};

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
  related_proposal: RelatedProposalSQL[] = [];
  comment = new Map<number/*comment_id*/, CommentSQL>();
}

export const realNameDomain = 'real_name';
export const emailDomain = 'email';
export const githubDomain = 'github.com';

export abstract class Persister<SEP_T> {
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

/**
 * Creates and initializes new SQLite data source
 * @param database file name, or in-memory
 * @param dropSchema true to start with clean database (will destroy any existing data), false to keep data
 * @returns data source
 */
export const dataSource = async (database: string, dropSchema: boolean): Promise<DataSource> =>
  await new DataSource({
    type: 'sqlite',
    database,
    entities: [ProjectSQL, PersonSQL, PersonUsernameSQL, ProposalSQL, ProposalRevisionSQL, StageHistorySQL, RelatedProposalSQL, CommentSQL],
    synchronize: true,
    dropSchema,
    // logging: true,
  }).initialize();

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
  await dataSource.getRepository(RelatedProposalSQL).save(db.related_proposal);
  await dataSource.getRepository(CommentSQL).save(db.comment.values().toArray());
};
