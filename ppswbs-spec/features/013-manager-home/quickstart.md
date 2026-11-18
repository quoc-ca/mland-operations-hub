# Quickstart: Manager Home validation

**Date created**: 2026-11-18
**Date updated**: 2026-11-18
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

**Feature**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Reads**: [contracts/reads.md](contracts/reads.md)

A guide to prove the page works end to end once it is built. It holds no implementation code.

## Prerequisites

- Backend and frontend running locally as in `ppswbs_backend/RUN-PROJ-DEV.md` and `ppswbs_frontend/RUN-PROJ-DEV.md`.
- The development database holds at least: one late payment waiting, two change requests waiting, one design review waiting, one session today (and, for the cancelled check, one cancelled session today), and some revenue this month.
- A Manager account and a Staff account from the project's seeded test accounts (credentials are in the seed file, never in a scenario).

## Scenarios

| # | Do | Expect |
| --- | --- | --- |
| 1 | Sign in as the Manager and open the home page | Waiting-work cards show the counts above; a queue without a count (not yet delivered) shows no card at all (FR-003, Clarification 1) |
| 2 | Open each shown card | The owning list opens and holds exactly as many items as the card said (FR-004, SC-002) |
| 3 | Clear all waiting items in one queue, then refresh | That card is dimmed and grouped as "no outstanding work" (FR-005) |
| 4 | Stop one source (for example make one read fail) and refresh | Only that card shows an unavailable state with a retry; the others keep their numbers; nothing shows 0 for the failed one (FR-006, SC-003) |
| 5 | Look at the today strip with a cancelled session scheduled | Sessions include the cancelled one, "of which 1 cancelled" is shown (FR-007) |
| 6 | Choose one branch | Only the strip changes; the queue cards do not (FR-008) |
| 7 | Compare the revenue card with the dashboard for this month | Same number (FR-009, SC-004); activating the card opens the dashboard |
| 8 | Produce a refused action as Staff, then reopen the home page | The refused attempt appears in the recent events, with a link to the audit log (FR-010) |
| 9 | Sign in as Staff and open the Manager home address | No Manager figure appears; the Staff home opens (FR-001, SC-006) |
| 10 | Let the session expire, then act | Sign-in appears and returns to the home page (FR-018) |
| 11 | Resize to 375 px | Cards stack, no horizontal scrolling (FR-016, SC-005) |
| 12 | Open the page on a normal connection with the wave-1 cards and time it from the request to every shown card or its own state being visible | Within 5 seconds (SC-001); record the measured time |

## Checks to run

- Frontend: `npm test` and `npm run build` in `ppswbs_frontend/` (the project has no lint script).
- Backend, for each read an owner adds: the owner's JUnit tests and a MySQL integration test that the count equals the list size, plus `ModulithArchitectureTests`.
- Hand the acceptance criteria (scenarios 1–12) to `ppswbs-testing/` for the browser scenario; they are not tasks of this feature.
