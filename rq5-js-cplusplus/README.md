# Proposal Scraper (JS & C++)

Small collection of scripts to scrape, parse, and store proposal data from JavaScript and C++ proposal repositories.

Project structure
- `js/` — JavaScript proposal scraping and processing scripts and data.
- `cplusplus/` — C++ proposal scraping and processing scripts and data.
- `output/` — Generated JSON and SQLite outputs.

Quick start
- Inspect the `js/` and `cplusplus/` folders for runnable scripts.
- Run the Python scripts `main.py` in those folders to generate output in `output/`.
- Run the Python test scripts `test_*.py` to validate the outputs.

Required local data (not in the repository)
- `cplusplus/data/cppreference/` is git-ignored because it holds third-party pages scraped from cppreference.com.
- Recreate it by saving the HTML of each "Compiler support for C++NN" page from https://en.cppreference.com/w/cpp/compiler_support (e.g. `.../compiler_support/20`) as `cplusplus/data/cppreference/<NN>.html` for NN in 11, 14, 17, 20, 23, 26.
- `cplusplus/proposal_statusses.py` reads these files. Check cppreference.com's licence terms before redistributing the pages.

Acknowledgements
- Development assisted by GitHub Copilot.

