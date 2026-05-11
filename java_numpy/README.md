# Environment Variables

```bash
GITHUB_TOKEN # auth token for github API
GITHUB_API_VERSION # version of github API (see github docs for format)
```

# Running scrapers

```bash
#Scraping JEP:
npm run scrape-jep <database file name> <cache folder name> <intermediate save folder name>
#Scraping NEP:
npm run scrape-nep <database file name> <cache folder name> <intermediate save folder name>
```

- **database file name**: where to save the final data; the file path relative to this directory will be `data/{name}.db`; if left out, then the database will be in memory
- **cache folder name**: where to cache results of web fetches; they will be cached by URL under the folder `cache/{name}/`; if left out, then no caching
- **intermediate save folder name**: where to save SEP data as JSON, after fetching and parsing; will be saved by ID under the file `data/{project}/{name}/{id}.json`; if left out, then no intermediate saving
