# Implementation Design Approval: Manager Home

**Date created**: 2026-11-18
**Date updated**: 2026-11-18
**Author**: Hồ Hoàng Quốc
**Last updated by**: Hồ Hoàng Quốc

## Approval status

**Status**: PARTIALLY APPROVED (wave 1 only)

## Approved revision and scope

- Design revision: `ppswbs-spec/features/013-manager-home/design/architecture.md` (sha256 prefix `0c19f90a9c59e822`) and `design/workflows.md` (sha256 prefix `045dc25e04730577`), as drafted on 2026-11-18; spec, plan, research, data-model and contracts/reads.md as merged in PR for 013 on develop.
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
