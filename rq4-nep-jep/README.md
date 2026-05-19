# Environment Variables

```bash
GITHUB_TOKEN # auth token for github API
GITHUB_API_VERSION # version of github API (see github docs for format)
```

# Setup

Clone the repo, install npm and uv, then:
```bash
cd <here>
npm i
uv sync
```

# Running scrapers

```bash
#Scraping JEP:
npm run scrape-jep \
  [+db-name <database file name>] \
  [+cache-dir <cache folder name>] \
  [+save-dir <intermediate save folder name>] \
  [+new-db]
#Scraping NEP:
npm run scrape-nep \
  [+db-name <database file name>] \
  [+cache-dir <cache folder name>] \
  [+save-dir <intermediate save folder name>] \
  [+new-db]
```

Yes, the parameter keys are prepended with `+` and they are optional. The parameter value comes right after the key.

Parameters:

- **database file name**: where to save the final data; the file path relative to this directory will be `data/{name}.db`; if left out, then the database will be in memory
- **cache folder name**: where to cache results of web fetches; they will be cached by URL under the folder `cache/{name}/`; if left out, then no caching
- **intermediate save folder name**: where to save SEP data as JSON, after fetching and parsing; will be saved by ID under the file `data/{project}/{name}/{id}.json`; if left out, then no intermediate saving
- **`new-db`**: flag that drops the database in the file if it exists and creates a new one

# Notes

For the exact schema that is output, see `src/persist.ts`.

JEP data is obtained from the [OpenJDK main website](https://openjdk.org/jeps/0) (metadata, content) and the [dedicated issues website](https://bugs.openjdk.org) (some metadata, comments).

NEP data is obtained from the [NumPy website](https://numpy.org/neps) (metadata, content), which also links to the [mailing list](https://mail.python.org/archives/list/numpy-discussion@python.org/) and [GitHub repo](https://github.com/numpy/numpy/) (discussions, stage history).

The file `nep-missing-dates.json` contains the oldest date from the GitHub history for the NEPs that didn't have a creation date written on the website. They may be used as creation dates.

The GitHub API can be accessed without a token, but the rate limit will be lower.

Leaving out the `new-db` flag will cause the new data to be appended, and may cause primary key conflicts and such. One situation where you may want to leave it out is if you want to dump both JEP and NEP into the same database. IDs are different between projects, so that shouldn't cause issues.
