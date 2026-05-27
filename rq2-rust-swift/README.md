# SEPScraper

A Java program to collect PR and related data concerning RFCs from the Rust RFC repository.\
Developed for the CSE3000 Research Project of the Computer Science & Engineering programme of the Delft University of Technology.

## Usage

Pre-compiled binaries are not provided.

### Prerequisites

- Java 26 JDK Installation
- Git
- A GitHub API token

### Installation and Execution

#### Required arguments:
`-local "PATH"` `PATH` should be a local path ending with a `/` pointing to a folder with read/write access. 
If the directory does not exist, it will be created.\
`-project PROJECT` `PROJECT` should be either `rust`, `swift`, or `all`. The `all` setting runs both and combines them in one SQLite database.\
`-token "TOKEN"` `TOKEN` should be a GitHub API token

#### Optional arguments:
`-ignorecache true` will make the program ignore any cached file reading data, but will still write new API requests to it.

#### Running the program

- Clone the repository and navigate to it with your preferred command line.
- Invoke `./gradlew run --args='ARGUMENTS HERE'`

#### Common Issues

"Toolchain installation '...' does not provide the required capabilities: [JAVA_COMPILER]"\
Ensure the Java JDK is installed, not the JRE.

### Output

In this context, `~/` will point to the directory specified by the `local` argument.

All fetched API requests are stored in the `~/cache` folder. Below is a list of all files. Words in all capitals are variables.
- An index of all pull requests are prepended by `_prIndex_PAGE.json`
- GitHub users are formatted as `gh_user_LOGIN.json`
- Conversations on pull requests are formatted as `conv_PRNUM.json`
- Comments on pull request reviews are formatted as `revc_PRNUM.json`
- Commits fetched from the GitHub API are formatted as `commit_HASH.json`
- File blobs from commits are formatted as `file_COMMITHASH_FILENAME.json`
- Commits associated with a pull request are formatted as `pr_commits_PRNUM.json`
- Indexes of categories from a Discourse forum are formatted as `discourse_category_CATID_PAGE.json`
- Replies to a topic from a Discourse forum are formatted as `discourse_replies_SLUG_ID_POSTNUM.json`

All collected data is exported to an SQLite database, located in `~/dataset.sqlite`.
A copy of the schema used can be found in this repository under `src/main/resources/schema.sql`\
If project argument is `rust` or `all`: A clone of the `rust-lang/rfcs` repository is located in `~/rfcs`\
If project argument is `swift` or `all`: A clone of the `swiftlang/swift-evolution` repository is located in `~/swift-evolution`

### License

This project is licensed under the **MIT License**, see the LICENSE file for details.

### Third-Party Licenses & Credits

This project relies on several open-source dependencies. A complete and detailed breakdown of all components, along with their full license texts, can be found in the THIRD-PARTY-LICENSES.md file.

### Dependency Overview

| Group / Dependency                                       | Version         | License Track Used     |
|:---------------------------------------------------------|:----------------|:-----------------------|
| **com.google.code.gson** : gson                          | 2.13.1          | Apache-2.0             |
| **com.google.errorprone** : error_prone_annotations      | 2.38.0          | Apache-2.0             |
| **com.googlecode.javaewah** : JavaEWAH                   | 1.2.3           | Apache-2.0             |
| **com.squareup.okhttp3** : okhttp / logging-interceptor  | 4.12.0 / 3.5.0  | Apache-2.0             |
| **com.squareup.okio** : okio / okio-jvm                  | 3.6.0           | Apache-2.0             |
| **com.squareup.retrofit2** : retrofit / converter-gson   | 3.0.0           | Apache-2.0             |
| **com.sun.istack** : istack-commons-runtime              | 4.1.2           | EDL-1.0 (BSD-3-Clause) |
| **commons-codec** : commons-codec                        | 1.21.0          | Apache-2.0             |
| **jakarta.activation** : jakarta.activation-api          | 2.1.4           | EDL-1.0 (BSD-3-Clause) |
| **jakarta.inject** : jakarta.inject-api                  | 2.0.1           | Apache-2.0             |
| **jakarta.persistence** : jakarta.persistence-api        | 3.2.0           | EDL-1.0 (BSD-3-Clause) |
| **jakarta.transaction** : jakarta.transaction-api        | 2.0.1           | EPL-2.0                |
| **jakarta.xml.bind** : jakarta.xml.bind-api              | 4.0.4           | EDL-1.0 (BSD-3-Clause) |
| **net.bytebuddy** : byte-buddy                           | 1.18.8          | Apache-2.0             |
| **org.antlr** : antlr4-runtime                           | 4.13.2          | BSD-3-Clause           |
| **org.eclipse.angus** : angus-activation                 | 2.0.3           | EDL-1.0 (BSD-3-Clause) |
| **org.eclipse.jgit** : org.eclipse.jgit                  | 7.6.0...        | BSD-3-Clause           |
| **org.glassfish.jaxb** : jaxb-core / jaxb-runtime / txw2 | 4.0.6           | EDL-1.0 (BSD-3-Clause) |
| **org.hibernate.models** : hibernate-models              | 1.1.1           | Apache-2.0             |
| **org.hibernate.orm** : hibernate-core / dialects        | 7.3.2.Final     | Apache-2.0             |
| **org.jboss.logging** : jboss-logging                    | 3.6.3.Final     | Apache-2.0             |
| **org.jetbrains** : annotations                          | 13.0            | Apache-2.0             |
| **org.jetbrains.kotlin** : kotlin-stdlib (and variants)  | 2.1.21 / 1.9.10 | Apache-2.0             |
| **org.jsoup** : jsoup                                    | 1.22.2          | MIT                    |
| **org.slf4j** : slf4j-api                                | 2.0.17          | MIT                    |
| **org.xerial** : sqlite-jdbc                             | 3.53.0.0        | Apache-2.0             |
