import sqlite3


DB_PATH = "./js/output/js_proposals.sqlite3"

def test_temporal_proposal():
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()


    # CREATE TABLE Proposal (
#     project_id INTEGER,
#     proposal_id TEXT,
#     proposer_id INTEGER,
#     topic TEXT,
#     proposal_type TEXT,
#     PRIMARY KEY (project_id, proposal_id),
#     FOREIGN KEY (project_id) REFERENCES Project(project_id),
#     FOREIGN KEY (proposer_id) REFERENCES Person(person_id)
# );
    proposal = cur.execute(
        """
        SELECT * FROM Proposal
        WHERE project_id = 1 AND proposal_id = 'Temporal';
        """
    ).fetchone()
    assert proposal is not None, "Temporal proposal not found in database"
    assert proposal[0] == 1
    assert proposal[1] == "Temporal", f"Expected proposal_id 'Temporal', got '{proposal[1]}'"
    assert proposal[2] == None
    assert proposal[3] == None
    assert proposal[4] == None

# CREATE TABLE ProposalRevision (
#     project_id INTEGER,
#     proposal_id TEXT,
#     revision_index INTEGER,
#     title TEXT NOT NULL,
#     created_at DATETIME NOT NULL,
#     content TEXT,
#     implemented_at_version TEXT,
#     PRIMARY KEY (project_id, proposal_id, revision_index),
#     FOREIGN KEY (project_id, proposal_id) REFERENCES Proposal(project_id, proposal_id)
# );
    proposalRevisions = cur.execute(
        """
        SELECT * FROM ProposalRevision
        WHERE project_id = 1 AND proposal_id = 'Temporal'
        ORDER BY created_at ASC;
        """
    ).fetchall()
    assert len(proposalRevisions) > 0, "No revisions found for Temporal proposal"

    assert proposalRevisions[-1][0] == 1
    assert proposalRevisions[-1][1] == "Temporal"
    assert proposalRevisions[-1][2] != None 
    assert proposalRevisions[-1][3] == "Temporal"
    assert proposalRevisions[-1][4] == "2026-04-03 09:23:09-07:00"
    assert proposalRevisions[-1][5].startswith("<!DOCTYPE html>")
    assert proposalRevisions[-1][5].endswith('<emu-import href="spec/intl.html"></emu-import>\n')

#     CREATE TABLE Comment (
#     comment_id INTEGER PRIMARY KEY,
#     author_id INTEGER,
#     project_id INTEGER,
#     proposal_id TEXT,
#     comment_on_comment_id INTEGER,
#     created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
#     content TEXT NOT NULL,
#     FOREIGN KEY (author_id) REFERENCES Person(person_id),
#     FOREIGN KEY (comment_on_comment_id) REFERENCES Comment(comment_id),
#     FOREIGN KEY (project_id, proposal_id) REFERENCES Proposal(project_id, proposal_id)
# );

    comments = cur.execute(
        """
        SELECT * FROM Comment
        WHERE project_id = 1 AND proposal_id = 'Temporal'
        ORDER BY created_at ASC;
        """
    ).fetchall()

    assert len(comments) == 9, "No comments found for Temporal proposal"

    assert comments[0][1] == None
    assert comments[0][2] == 1
    assert comments[0][3] == "Temporal"
    assert comments[0][4] == None
    assert comments[0][5] == "2018-09-27 00:00:00"
    assert comments[0][6].startswith("(Maggie Pint)\n\n- [prop")

#     CREATE TABLE StageHistory (
#     project_id INTEGER,
#     proposal_id TEXT,
#     stage_index INTEGER,
#     -- ENUM does not exist in SQLite, so we use CHECK constraint
#     status TEXT NOT NULL,
#     created_at DATETIME NOT NULL,
#     PRIMARY KEY (project_id, proposal_id, stage_index ),
#     FOREIGN KEY (project_id, proposal_id) REFERENCES Proposal(project_id, proposal_id)
# );

    stageHistory = cur.execute(
        """
        SELECT * FROM StageHistory
        WHERE project_id = 1 AND proposal_id = 'Temporal'
        ORDER BY created_at ASC;
        """
    ).fetchall()

    assert len(stageHistory) > 0
    assert stageHistory[0][0] == 1
    assert stageHistory[0][1] == "Temporal"
    assert stageHistory[0][2] == 0
    assert stageHistory[0][3] == "Stage 1"
    assert stageHistory[0][4] == "2017-04-03 04:21:18"

    conn.close()


if __name__ == "__main__":
    test_temporal_proposal()
    