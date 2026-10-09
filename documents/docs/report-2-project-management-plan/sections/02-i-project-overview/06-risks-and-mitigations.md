## 6. Risks and Mitigations

The ratings below are provisional planning assessments based on the open dependencies recorded in the project materials. The team should review probability, impact, and role ownership at each iteration review; no individual owner is assigned in this draft.

| ID | Risk | Probability (Provisional) | Impact (Provisional) | Mitigation / Proposed Role |
|---|---|---|---|---|
| R-01 | Capacity, product capability, catalogue, prices, or feasibility values may be approved too late for reliable booking and sales. | High | High | Confirm values before accepting affected workflows; log unresolved decisions. Proposed role: BA / PL. |
| R-02 | Provider contracts, credentials, quotas, or sandbox access may delay integration. | Medium | High | Confirm prerequisites early, protect credentials, and test in approved environments. Proposed role: OPS. |
| R-03 | Late payment events or pending/failed refunds may lack an agreed operational response. | Medium | High | Agree exception ownership and test cases before release; never fulfil from a Return URL or late event. Proposed role: BA / QA. |
| R-04 | Privacy, retention, attribution, or delivery-data controls may be missing from approved policies. | Medium | High | Obtain business/legal review before go-live; verify retention and attribution controls. Proposed role: PL / OPS. |
| R-05 | Integration complexity, rework, or academic workload may exceed the 14-week estimate. | Medium | Medium | Review priorities each cycle, monitor the reserve, and reforecast before the testing window is affected. Proposed role: PL. |
