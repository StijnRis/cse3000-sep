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

export type Comment<ID, A> = {
  id: ID,
  replyTo?: ID,
  author: A,
  created: Date,
  body: string,
};
export type Email = Comment<string, EmailUser>;
export type GithubComment = Comment<number, GithubUser>;

export type GithubCommentJSON = {
  id: number,
  user: { login: string },
  created_at: string,
  body: string,
  in_reply_to_id?: number,
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
