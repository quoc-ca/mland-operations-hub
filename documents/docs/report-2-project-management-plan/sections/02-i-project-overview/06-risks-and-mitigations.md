## 6. Risks and Mitigations

::: {.docx-table width="100%" columns="8%,30%,11%,11%,40%" repeat-header="false"}

| # | Risk | Prob | Impact | Mitigation |
|---|---|---|---|---|
| R01 | Mland changes business rules after the requirements are approved, for example deposit or refund rules. | High | High | Freeze the SRS at the end of week 5. Accept a change only after an impact analysis, then update spec, code, and tests together. |
| R02 | Automated tests do not cover enough code by the end of Iteration 2. | Medium | High | Each feature owner writes unit tests with every change. Review test results at each iteration demo; reserve one day in Iteration 3 for test catch-up. |
| R03 | Complex flows – workshop booking, payment confirmation, and settlement – have hard-to-reproduce bugs. | Medium | High | Test these flows on MySQL, not an in-memory database. Run end-to-end tests in a real browser against the VNPay sandbox. |
| R04 | External services (VNPay, Firebase, GHTK, Cloudinary) are unavailable or change their sandbox. | Medium | Medium | Integrate each provider early behind an adapter. Keep a mock for local development and demos. |
| R05 | Members work on a shared branch and overwrite each other's work, or workload becomes uneven. | Medium | Medium | Each member works on a personal branch and merges into develop through a pull request. The Team Leader rebalances tasks every week. |

:::
