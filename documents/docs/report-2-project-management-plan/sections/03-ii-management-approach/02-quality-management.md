## 2. Quality Management

The team builds quality into every step instead of testing only at the end.

| Approach | Practice |
|---|---|
| Defect prevention | Every feature starts from an approved specification with acceptance scenarios. Business rules are clarified with Mland before coding. |
| Reviewing | Specifications are reviewed by the feature owner and the Team Leader. Code is merged into `develop` only through a reviewed pull request. |
| Unit testing | Backend code is tested with JUnit 5 and Spring Modulith tests; frontend code with Vitest and Testing Library. Tests run before every merge. |
| Integration testing | Critical flows run against a real MySQL database, including Flyway migrations. |
| System testing | End-to-end scenarios run in a real browser; performance is checked with k6. |
| Defect tracking | Each defect is logged in GitHub Issues with steps to reproduce, expected and actual results, and severity. Critical defects must be fixed and retested before release. |
| Database safety | Applied Flyway migrations are never edited; every schema change is a new migration. |
