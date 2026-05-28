from enum import Enum
from datetime import datetime
from typing import final
from sqlalchemy import ForeignKey, ForeignKeyConstraint, DateTime
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column
from sqlalchemy import Enum as EnumCol

class Base(DeclarativeBase):
  pass

@final
class Project(Base):
  __tablename__ = 'Project'
  project_id: Mapped[int] = mapped_column(primary_key=True)
  project_name: Mapped[str] = mapped_column()
  enhancement_proposal_name: Mapped[str] = mapped_column()
  copyright: Mapped[str] = mapped_column()

@final
class Person(Base):
  __tablename__ = 'Person'
  person_id: Mapped[int] = mapped_column(primary_key=True)
  full_name: Mapped[str | None] = mapped_column(nullable=True)

@final
class Organisation(Base):
  __tablename__ = 'Organisation'
  organisation_id: Mapped[int] = mapped_column(primary_key=True)
  organisation_name: Mapped[str] = mapped_column(nullable=False)

@final
class Affiliation(Base):
  __tablename__ = 'Affiliation'
  organisation_id: Mapped[int] = mapped_column(ForeignKey(Organisation.organisation_id), primary_key=True)
  person_id: Mapped[int] = mapped_column(ForeignKey(Person.person_id), primary_key=True)

@final
class PersonIdentifier(Base):
  __tablename__ = 'PersonIdentifier'
  person_id: Mapped[int] = mapped_column(ForeignKey(Person.person_id), primary_key=True)
  domain: Mapped[str] = mapped_column(primary_key=True)
  identifier_type: Mapped[str] = mapped_column(primary_key=True)
  identifier: Mapped[str] = mapped_column(primary_key=True)

@final
class Proposal(Base):
  __tablename__ = 'Proposal'
  project_id: Mapped[int] = mapped_column(ForeignKey(Project.project_id), primary_key=True)
  proposal_id: Mapped[str] = mapped_column(primary_key=True)
  topic: Mapped[str | None] = mapped_column(nullable=True)
  proposal_type: Mapped[str | None] = mapped_column(nullable=True)
  # comments: Mapped[list['Comment']] = relationship()

class NormalisedStatus(str, Enum):
  accepted = 'accepted'
  draft = 'draft'
  review = 'review'
  superseded = 'superseded'
  withdrawn = 'withdrawn'
  rejected = 'rejected'
  unknown = 'unknown'

@final
class ProposalStatus(Base):
  __tablename__ = 'ProposalStatus'
  project_id: Mapped[int] = mapped_column(primary_key=True)
  proposal_id: Mapped[str] = mapped_column(primary_key=True)
  status_index: Mapped[int] = mapped_column(primary_key=True)
  normalised_status: Mapped[NormalisedStatus] = mapped_column(EnumCol(NormalisedStatus))
  raw_status: Mapped[str | None] = mapped_column(nullable=True)
  created_at: Mapped[datetime] = mapped_column(DateTime(), nullable=False)
  __table_args__: tuple[ForeignKeyConstraint] = (
    ForeignKeyConstraint([project_id, proposal_id], [Proposal.project_id, Proposal.proposal_id]),
  )

@final
class ProposalRevision(Base):
  __tablename__ = 'ProposalRevision'
  project_id: Mapped[int] = mapped_column(primary_key=True)
  proposal_id: Mapped[str] = mapped_column(primary_key=True)
  revision_index: Mapped[int] = mapped_column(primary_key=True)
  title: Mapped[str] = mapped_column(nullable=False)
  created_at: Mapped[datetime] = mapped_column(DateTime(), nullable=False)
  content: Mapped[str] = mapped_column()
  implemented_at_version: Mapped[str | None] = mapped_column(nullable=True)
  __table_args__: tuple[ForeignKeyConstraint] = (
    ForeignKeyConstraint([project_id, proposal_id], [Proposal.project_id, Proposal.proposal_id]),
  )

@final
class ProposalRevisionAuthor(Base):
  __tablename__ = 'ProposalRevisionAuthor'
  project_id: Mapped[int] = mapped_column(primary_key=True)
  proposal_id: Mapped[str] = mapped_column(primary_key=True)
  revision_index: Mapped[int] = mapped_column(primary_key=True)
  author_id: Mapped[int] = mapped_column(ForeignKey(Person.person_id), primary_key=True)
  __table_args__: tuple[ForeignKeyConstraint] = (
    ForeignKeyConstraint([project_id, proposal_id, revision_index],[ProposalRevision.project_id, ProposalRevision.proposal_id, ProposalRevision.revision_index]),
  )

class RelationType(str, Enum):
  related = 'related'
  supersedes = 'supersedes'

@final
class RelatedProposal(Base):
  __tablename__ = 'RelatedProposal'
  project_id: Mapped[int] = mapped_column(primary_key=True)
  proposal_id: Mapped[str] = mapped_column(primary_key=True)
  related_project_id: Mapped[int] = mapped_column(primary_key=True)
  related_proposal_id: Mapped[str] = mapped_column(primary_key=True)
  relation_type: Mapped[RelationType] = mapped_column(EnumCol(RelationType), name='type', nullable=False)
  __table_args__: tuple[ForeignKeyConstraint, ForeignKeyConstraint] = (
    ForeignKeyConstraint([project_id, proposal_id], [Proposal.project_id, Proposal.proposal_id]),
    ForeignKeyConstraint([related_project_id, related_proposal_id], [Proposal.project_id, Proposal.proposal_id])
  )

@final
class Comment(Base):
  __tablename__ = 'Comment'
  comment_id: Mapped[int] = mapped_column(primary_key=True)
  author_id: Mapped[int] = mapped_column(ForeignKey(Person.person_id), nullable=False)
  project_id: Mapped[int] = mapped_column(nullable=False)
  proposal_id: Mapped[str] = mapped_column(nullable=True)
  comment_on_comment_id: Mapped[int | None] = mapped_column(ForeignKey(comment_id), nullable=True)
  created_at: Mapped[str] = mapped_column()
  content: Mapped[str] = mapped_column(nullable=False)
  # __table_args__ = (
  #   ForeignKeyConstraint([project_id, proposal_id], [Proposal.project_id, Proposal.proposal_id]),
  # )