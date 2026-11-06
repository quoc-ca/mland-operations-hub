---
name: scout
description: Fast, token-efficient codebase scouting for file discovery, task context, and codebase orientation. Use before multi-directory changes, debugging, architecture questions, or when repository facts can be discovered locally.
---

# Scout

Find the smallest evidence set that answers the request. Prefer `rg` and targeted directory inspection; read the relevant code, tests, and configuration rather than dumping broad search results.

1. Parse the request into symbols, paths, behaviors, data flows, and likely entry points.
2. Map only the relevant implementation, callers, contracts, tests, configuration, and established conventions.
3. Split work only when scopes are independent and exact; avoid duplicated discovery.
4. Report evidence compactly:
   - **Relevant files** — path and why it matters.
   - **Current patterns and constraints** — facts observed in the repository.
   - **Unresolved questions** — only questions that cannot be answered from inspected evidence.

Do not invoke external models, use Claude task syntax, or launch broad scans without a concrete purpose. Use graph-style analysis only for genuinely large, repeated structural relationships.
