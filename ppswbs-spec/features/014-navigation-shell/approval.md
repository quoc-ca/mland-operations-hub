# Implementation Design Approval: Manager Home
> **Renamed 2026-11-25:** this folder was `013-manager-home`; it is now `014-navigation-shell`. The spec covers the navigation shell only; the Manager Home dashboard blocks moved into `010-manager-dashboard` (FR-034 to FR-038). This file keeps the earlier Manager Home history and was not rewritten; only the folder path was updated.

**Date created**: 2026-11-18
**Date updated**: 2026-11-22
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Approval status

**Status**: PARTIALLY APPROVED (wave 1 only)

## Approved revision and scope

- Design revision: `ppswbs-spec/features/014-navigation-shell/design/architecture.md` (sha256 prefix `0c19f90a9c59e822`) and `design/workflows.md` (sha256 prefix `045dc25e04730577`), as drafted on 2026-11-18; spec, plan, research, data-model and contracts/reads.md as merged in PR for 013 on develop.
- Approved implementation scope: wave 1 in full, i.e. the frontend tasks that need no backend change: T002–T013, T021–T022, T025–T029, T032–T037 (late payments, change requests, design review cards; session and cancelled counts; revenue; Operations audit events; shortcuts; polish). Workflows WF-01 to WF-07.
- Approval date: 2026-11-18
- Tech Lead: Hồ Hoàng Quốc
- Source of approval: stated by Hồ Hoàng Quốc in the Claude Code session on 2026-11-18 ("Tech Lead là Hồ Hoàng Quốc, duyệt toàn bộ wave 1, ngày 2026-11-18").

## Decisions approved

| Decision | Approved value | Evidence/source | Affected artifact revision |
| --- | --- | --- | --- |
| Wave 1 frontend design | New slice `features/managerhome`, units and data flows DF-1 to DF-6 as in architecture.md | session statement above | revision above |

## Exclusions and remaining blockers

- Scope extension 2026-11-18: Hồ Hoàng Quốc, in this session ("Làm backend"), asked for the owner-side reads, and (relayed from the parent session) as owner of Feature 003 agreed to R-1, R-2, R-3, R-4 and R-5 with the definitions in contracts/reads.md section B. They are built; R-3 has a card because a confirmation list was added to the cash-close screen on the same day. T020 and the review-moderation card are dropped by owner decision 2026-11-18.
- Open points not decided in the approval statement (architecture.md O-3, O-5, O-7; workflows.md O-7, O-8): implementation applies the closest reading of the spec and records it in the task notes; a card whose list cannot be shown to match its count (FR-004) is not registered.
- The owner-agreement part of T001 is not obtained and is outside this scope.

## Navigation shell and merged dashboard (2026-11-22)

- Scope: spec "Session 2026-11-22" and tasks T038–T044 (rail of 10 groups, tabs, page header with actions, `useNavBadges`, one dashboard page at `/manager/dashboard`).
- Approval date: 2026-11-22. Approver: Hồ Hoàng Quốc.
- Source of approval: the owner's instruction to the implementing session, which names the design as approved on the prototype `claude.ai/artifact/AB3ZMi6aiCwUnS4X6rS28B`. Dropped by the owner on 2026-11-22: the overdue block (T042). Not built in this change: Staff badges, Ctrl K search.

## Split and rename (2026-11-25)

- The folder `013-manager-home` was renamed `014-navigation-shell` so that number 013 belongs only to `013-staff-consultation-chat`. The Manager Home dashboard blocks moved into `010-manager-dashboard` (tasks T064 to T071); the shell and badges stay here.
- **No new approval is granted by this split** and no approved scope was widened or narrowed. The approvals recorded above keep their original date and approver; they now cover, in this folder, the shell (T038 to T044 scope) and the owner count reads, and, in 010, the blocks whose tasks moved.
- The `sha256` prefixes recorded above are of the 2026-11-18 drafts. Before the 2026-11-25 banner, `design/architecture.md` (`a03d1207…`) and `design/workflows.md` (`47a5f40b…`) already differed from them, so the recorded revision no longer identifies the files as they are today. This was already the case before the split. The Tech Lead needs to re-record the approved revision if the approval is to be relied on for the current design.
- `design/` files only received a status banner on 2026-11-25; their content is unchanged.
