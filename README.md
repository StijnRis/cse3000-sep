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