export type SEP<T, C> = {
  id: number,
  title: string,
  type: T,
  supersederID?: number,
  comments: C[],
};

export type EmailUser = {
  email: string,
  name: string,
};
export type GithubUserJSON = {
  login: string,
  name: string,
};
export type GithubOrganisation = {
  login: string,
  name?: string,
};
export type GithubUser = GithubUserJSON & {
  affiliations: GithubOrganisation[],
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

export type GithubCommit = {
  sha: string,
  author: {
    login: string,
  },
  commit: {
    author: {
      date: string,
    },
  },
};

export type GithubCommitDetailed = GithubCommit & {
  files: {
    filename: string,
    raw_url: string,
  }[],
};
