# Open-Source Governance Data Extractors (CSE3000)

This repository contains the suite of Python data scrapers developed for the **CSE3000 Research Project** at Delft University of Technology. The tools are designed to extract structured longitudinal data from Software Enhancement Proposal (SEP) systems and map them into a unified schema to enable cross-project governance analysis.

The final research papers and project insights will be available on the official project website: [cse3000-research-project.github.io](https://cse3000-research-project.github.io/).

## Ecosystem Scraper Distribution

A five-student team collaborated to build the data extractors, with each researcher taking ownership of two distinct software ecosystems:

| Ecosystem System | Developer |
| :--- | :--- |
| **PEP** (Python) | Tim Raaymakers |
| **PDEP** (Pandas) | Tim Raaymakers |
| **NEP** (NumPy) | Catalin Moldovan |
| **JEP** (Java / OpenJDK) | Catalin Moldovan |
| **TC39** (JavaScript) | Stijn Risseeuw |
| **ISO Proposals** (C++) | Stijn Risseeuw |
| **Rust RFCs** (Rust) | Cas Ebels |
| **Evolution Proposals** (Swift) | Cas Ebels |
| **KEP** (Kubernetes) | Kajus Kuncaitis |
| **KEEP** (Kotlin) | Kajus Kuncaitis |

## Repository Structure & Execution

Each ecosystem's pipeline is organized into its own dedicated folder within this repository. Because every software ecosystem utilizes different platforms for tracking proposals (e.g., GitHub, mailing lists, or dedicated web archives), the codebase is modularized:

* **Independent Pipelines**: Each folder contains a standalone scraper tailored specifically to that ecosystem's data structures.
* **Local Documentation**: You will find an individual `README.md` file inside each ecosystem's folder detailing its specific prerequisites, setup instructions, and execution commands.


## Data sources used

* **OpenJDK:**
    * **Proposals:** [https://openjdk.org/jeps/0](https://openjdk.org/jeps/0)
    * **Issues & Changelogs:** [https://bugs.openjdk.org](https://bugs.openjdk.org)
    * **Mailing Lists:** [https://mail.openjdk.org](https://mail.openjdk.org)


* **NumPy:**
    * **Proposals:** [https://numpy.org/neps](https://numpy.org/neps), [https://github.com/numpy/numpy/tree/main/doc/neps](https://github.com/numpy/numpy/tree/main/doc/neps)
    * **Discussions:** [https://mail.python.org/archives/list/numpy-discussion@python.org/](https://mail.python.org/archives/list/numpy-discussion@python.org/)
    * **Resolution Comments:** GitHub issues and pull requests linked from the main NumPy website.


* **Python:**
    * **Identifiers:** [https://peps.python.org](https://peps.python.org)
    * **Content & Revisions:** [https://github.com/python/peps](https://github.com/python/peps)
    * **Mailing Lists:** [https://mail.python.org/mailman3](https://mail.python.org/mailman3)
    * **Discussion Forum:** [https://discuss.python.org/c/peps/19](https://discuss.python.org/c/peps/19)


* **Pandas:**
    * **Proposals & Code:** [https://github.com/pandas-dev/pandas](https://github.com/pandas-dev/pandas)


* **ISO C++:**
    * **Content (1992–present):** [https://www.open-std.org/jtc1/sc22/wg21/docs/papers/](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/)
    * **Tracking (Compiler):** [https://cppreference.com/cpp/compiler_support/11](https://cppreference.com/cpp/compiler_support/11)
    * **Tracking (2016–present):** [https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2026/sd-1.htm](https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2026/sd-1.htm)
    * **Tracking (2019–present):** [https://github.com/cplusplus/papers/issues](https://github.com/cplusplus/papers/issues)
    * **Mailing List (2019–present):** [https://lists.isocpp.org/mailman/listinfo.cgi/](https://lists.isocpp.org/mailman/listinfo.cgi/)


* **JavaScript (TC39):**
    * **Proposal Status:** [https://github.com/tc39/proposals](https://github.com/tc39/proposals)
    * **Proposal Content:** Individual repositories, for example: [https://github.com/tc39/proposal-defer-import-eval](https://github.com/tc39/proposal-defer-import-eval)
    * **Meeting Notes:** [https://github.com/tc39/notes/tree/main/meetings](https://github.com/tc39/notes/tree/main/meetings)


* **Rust:**
    * **Proposals:** [https://github.com/rust-lang/rfcs](https://github.com/rust-lang/rfcs)


* **Swift:**
    * **Accepted Proposals:** [https://github.com/swiftlang/swift-evolution](https://github.com/swiftlang/swift-evolution)
    * **Discussions & Pitches:** [https://forums.swift.org/c/evolution/proposal-reviews/21](https://forums.swift.org/c/evolution/proposal-reviews/21)


* **Kubernetes:**
    * **Enhancements:** [https://github.com/kubernetes/enhancements](https://github.com/kubernetes/enhancements)


* **Kotlin:**
    * **Proposals:** [https://github.com/Kotlin/KEEP](https://github.com/Kotlin/KEEP)