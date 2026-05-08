import datetime
import sqlite3

import requests

from cplusplus.emails import parse_message_page

DB_PATH = "./cplusplus/output/cplusplus_comments.sqlite3"


def test_email_17268():
    response = requests.get("https://lists.isocpp.org/std-proposals/2026/02/17268.php")
    html = response.text
    comment = parse_message_page(
        "https://lists.isocpp.org/std-proposals/2026/02/17268.php", html
    )
    assert comment.message_id == 17268
    assert comment.reply_to_message_id == 17267
    assert comment.author_name == "Arthur O'Dwyer"
    assert comment.author_email == "arthur.j.odwyer_at_[hidden]"
    assert comment.date == datetime.datetime(2026, 2, 27, 16, 32, 2)
    assert comment.content.startswith("On Fri, Feb 27, 2026 at ")
    assert comment.content.endswith("–Arthur")


def check_all_number_exist():
    # get all ids from database, then check if ther are any missing numbers in the sequence
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    ids = cur.execute(
        """
        SELECT comment_id FROM Comment
        WHERE project_id = 1
        ORDER BY comment_id ASC;
        """
    ).fetchall()
    conn.close()
    ids = [id[0] for id in ids]
    for i in range(ids[0], ids[-1] + 1):
        if i not in ids:
            print(f"❗Missing comment id {i}")


if __name__ == "__main__":
    print("Testing email parsing...")
    test_email_17268()
    check_all_number_exist()
    print("All tests passed!")
