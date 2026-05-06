export type SEP<A, T, S, C> = {
  id: number,
  title: string,
  authors: A[],
  type: T,
  status: S,
  supersederID?: number,
  created: Date,
  content: string,
  comments: C[],
};

export type EmailUser = {
  email: string,
  name: string,
};
export type GithubUser = {
  login: string,
  name: string,
};

export type Comment<A> = {
  author: A,
  created: Date,
  body: string
};
export type Email = Comment<EmailUser>;
export type GithubComment = Comment<GithubUser> & {
  id: number,
  reply_to?: number,
};

export type GithubCommentJSON = {
  id: number,
  user: { login: string },
  created_at: string,
  body: string,
  in_reply_to_id?: number,
};

export type ProjectSQL = {
  project_id: number,
  project_name: string,
  enhancement_proposal_name: string,
  copyright: string,
};

export type PersonSQL = {
  person_id: number,
};

export type PersonUsernameSQL = {
  person_id: number, //fk
  domain: string,
  username: string,
  real_name: string,
};

export type ProposalSQL = {
  project_id: number, //fk
  proposal_id: string,
  proposer_id?: number, //fk'
  topic?: string,
  proposal_type?: string,
};

export type ProposalRevisionSQL = {
  project_id: number, //fk'
  proposal_id: string, //fk'
  revision_index: number,
  title: string,
  created_at: Date,
  content: string,
  implemented_at_version?: string,
};


// export type RelatedProposal (
//     project_id INTEGER,
//     proposal_id TEXT,
//     related_project_id INTEGER,
//     related_proposal_id TEXT,
//     PRIMARY KEY (
//         project_id,
//         proposal_id,
//         related_project_id,
//         related_proposal_id
//     ),
//     FOREIGN KEY (project_id, proposal_id) REFERENCES Proposal(project_id, proposal_id),
//     FOREIGN KEY (related_project_id, related_proposal_id) REFERENCES Proposal(project_id, proposal_id)
// );

export type ProposalRevisionAuthorSQL = { //many to many
  project_id: number, //fk'
  proposal_id: string, //fk'
  revision_index: number, //fk'
  author_id: number, //fk
};

export type CommentSQL = {
  comment_id: number,
  author_id: number, //fk
  project_id: number, //fk'
  proposal_id: string, //fk'
  comment_on_comment_id?: number, //fk''
  created_at: Date,
  content: string,
};
