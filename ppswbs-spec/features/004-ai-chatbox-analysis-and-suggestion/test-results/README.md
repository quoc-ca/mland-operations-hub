# Feature-004 Test Evidence Status

Date updated: 2026-11-20  
Author: Ngô Tiến Đạt  
Last updated by: Ngô Tiến Đạt  

The existing E2E files in this directory include both retired AI scenarios and
the current human-review/custom-manufacturing scenarios. The retired AI files
(`E2E-004-001`, `E2E-004-004`, `E2E-004-005`) are historical evidence only and
must not be marked as acceptance evidence for the current baseline.

The complete handoff and new scenario catalog are in:

`ppswbs-testing/TEST-HANDOFF-2026-11-21--feature-004-012-custom-manufacturing.md`

New scenarios must cover Member creation, five-image limit, image-quality
blocking, Member ownership, Staff/Manager equal
review authority, mandatory explanation, decision conflicts, quote expiry, full
payment, production/fulfilment, Admin denial, retry, privacy and UTF-8 behavior.
Each scenario must follow `ppswbs-testing/AGENT.md` and use format version 2.

The current contract is Member-only. Historical Guest creation, quota and
tracking files remain evidence of the former baseline and must be marked retired;
new acceptance scenarios must assert sign-in gating and no Guest persistence.
