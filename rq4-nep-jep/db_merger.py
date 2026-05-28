from os import getenv
from dotenv import load_dotenv
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session
from pyutil.entities import *

_ = load_dotenv()
src_files = getenv('INPUT_DB_FILES')
src_files = src_files.split(',') if src_files else []
out_file = 'test.db'
out_eng = create_engine(f'sqlite:///data/{out_file}')
Base.metadata.create_all(out_eng)
out_ses = Session(out_eng)

for src_file in src_files:
  print(f'Dumping {src_file}')
  src_eng = create_engine(f'sqlite:///data/{src_file}')
  Base.metadata.create_all(src_eng)
  src_ses = Session(src_eng)
  for ent in [Project, Person, Organisation, Affiliation, PersonIdentifier, Proposal, ProposalRevision, ProposalRevisionAuthor, ProposalStatus, RelatedProposal, Comment]:
    for src_obj in src_ses.scalars(select(ent)):
      out_obj = out_ses.merge(src_obj)
      out_ses.add(out_obj)
out_ses.commit()