## 1. Project Process/Methodology

The team applies the **Iterative and Incremental** development model. After initial planning, the product is built in three two-week iterations (weeks 6–11). Each iteration repeats the same cycle and adds a working increment that is demonstrated to the supervisor and the Mland representative.

{{r2-process-model width=90% align=center}}

*Figure 1. Iterative and Incremental Development Model*

| Phase | Activities in this project | Output |
|---|---|---|
| Initial planning | Define the project scope, features, schedule, and roles (Stages 1–3). | Report 1, Report 2, initial SRS and SDS |
| Planning | Select the features and tasks for the iteration; each feature has one owner. | Iteration scope and task list |
| Requirements | Write or update the feature specification and clarify open rules with Mland. | Approved feature spec, SRS update |
| Analysis & design | Design the data model, API contracts, and screens. | SDS update |
| Implementation | Code and unit-test each task on the member's branch; merge into `develop` through a reviewed pull request. | Code package, unit test report |
| Testing | Run integration and end-to-end tests on the merged build. | Integration test report |
| Evaluation | Demonstrate the increment and record feedback for the next iteration. | Feedback, updated backlog |
| Deployment | Merge `develop` into `main` and deploy the release to the demo server. | Software package version |

The model suits the project because Mland's rules were still being clarified during development. Each iteration exposes misunderstandings early, while the most important workflows are delivered first.
