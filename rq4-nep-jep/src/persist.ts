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
  @Column({type: 'text', nullable: true})
  full_name?: string;
  @ManyToMany(() => OrganisationSQL) @JoinTable({
    name: 'Affiliation',
    joinColumn: {name: 'person_id', referencedColumnName: 'person_id'},
    inverseJoinColumn: {name: 'organisation_id', referencedColumnName: 'organisation_id'},
  })
  affiliations: OrganisationSQL[];
};

@Entity('PersonIdentifier')
class PersonIdentifierSQL {
  @PrimaryColumn({type: 'integer'})
  person_id: number;
  @ManyToOne(() => PersonSQL) @JoinColumn({name: 'person_id', referencedColumnName: 'person_id'})
  person: PersonSQL; //fk
  @PrimaryColumn({type: 'text'})
  domain: string;
  @PrimaryColumn({type: 'text'})
  identifier_type: 'email' | 'username' | 'display_name';
  @PrimaryColumn({type: 'text'})
  identifier: string;
};

@Entity('Organisation')
export class OrganisationSQL {
  @PrimaryColumn({type: 'integer'})
  organisation_id: number;
  @Column({type: 'text'})
  organisation_name: string;
}

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
  @Column({type: 'text', nullable: true})
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

@Entity('ProposalStatus')
class ProposalStatusSQL {
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
  status_index: number;
  @Column({type: 'text'})
  normalised_status: 'accepted' | 'rejected' | 'draft' | 'review' | 'withdrawn' | 'superseded' | 'unknown';
  @Column({type: 'text'})
  raw_status: string;
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
  @Column({type: 'text', nullable: true})
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
  @Column({type: 'datetime', nullable: true})
  created_at: string; //date
  @Column({type: 'text'})
  content: string;
};

class Database {
  project = new Map<number/*project_id*/, ProjectSQL>();
  organisation = new Map<number, OrganisationSQL>();
  person = new Map<number/*person_id*/, PersonSQL>();
  // combining values into string as a hack, because arrays don't have proper equals functionality
  person_identifier = new Map<string/*person_id domain identifier_type identifier*/, PersonIdentifierSQL>();
  proposal = new Map<string/*project_id proposal_id*/, ProposalSQL>();
  proposal_revision = new Map<string/*project_id proposal_id revision_index*/, ProposalRevisionSQL>();
  proposal_status = new Map<string/*project_id proposal_id stage_index*/, ProposalStatusSQL>();
  related_proposal = new Map<string/*project_id proposal_id related_project_id related_proposal_id*/, RelatedProposalSQL>();
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

  personDetailsToID = new Map<string/*domain identifier_type identifier*/, number>();
  /**
   * Gets person entity for given details. Creates person if doesn't exist. Does not persist identifiers
   * Updates full_name and affiliations if specified
   * @param domain 
   * @param identifier_type 
   * @param identifier 
   * @param full_name 
   * @param affiliations
   * @returns person
   */
  ensurePerson(
    domain: string, identifier_type: PersonIdentifierSQL['identifier_type'], identifier: string,
    full_name?: string, affiliations?: OrganisationSQL[],
  ): PersonSQL {
    const key = `${domain} ${identifier_type} ${identifier}`;
    const existID = this.personDetailsToID.get(key);
    let person: PersonSQL;
    if(existID !== undefined) {
      person = this.db.person.get(existID)!;
      if(full_name !== undefined) person.full_name = full_name;
      if(affiliations !== undefined) person.affiliations = affiliations;
      this.db.person.set(existID, person);
    } else {
      const newID = this.generateNumID(this.personDetailsToID.size);
      this.personDetailsToID.set(key, newID);
      person = {
        person_id: newID,
        full_name,
        affiliations: affiliations || [],
      };
      this.db.person.set(newID, person);
    }
    return person;
  }
  addPersonDetails(person_id: number, domain: string, identifier_type: PersonIdentifierSQL['identifier_type'], identifier: string) {
    const person = this.db.person.get(person_id)!;
    this.db.person_identifier.set(`${person_id} ${domain} ${identifier_type} ${identifier}`, {
      person_id,
      person,
      domain,
      identifier_type,
      identifier,
    });
  }

  /**
   * Implementations should come up with their own translation schemes
   * @param status raw status
   */
  abstract normalizeStatus(status: string | undefined): ProposalStatusSQL['normalised_status'];

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
    entities: [ProjectSQL, OrganisationSQL, PersonSQL, PersonIdentifierSQL, ProposalSQL, ProposalRevisionSQL, ProposalStatusSQL, RelatedProposalSQL, CommentSQL],
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
  await dataSource.getRepository(OrganisationSQL).save(db.organisation.values().toArray());
  await dataSource.getRepository(PersonSQL).save(db.person.values().toArray());
  await dataSource.getRepository(PersonIdentifierSQL).save(db.person_identifier.values().toArray(), { chunk: 500 });
  await dataSource.getRepository(ProposalSQL).save(db.proposal.values().toArray());
  await dataSource.getRepository(ProposalRevisionSQL).save(db.proposal_revision.values().toArray(), { chunk: 500 });
  await dataSource.getRepository(ProposalStatusSQL).save(db.proposal_status.values().toArray(), { chunk: 500 });
  await dataSource.getRepository(RelatedProposalSQL).save(db.related_proposal.values().toArray());
  await dataSource.getRepository(CommentSQL).save(db.comment.values().toArray());
};
