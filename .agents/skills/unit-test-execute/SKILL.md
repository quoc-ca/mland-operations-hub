---
name: unit-test-execute
description: Run one validated PPSWBS Java unit-test case through Maven Surefire and record its actual result in that case file.
metadata:
  short-description: Execute a documented Java unit test and record Surefire evidence
---

# Java Unit-Test Execute

Use only after `$unit-test-specify` has produced a valid one-method/one-case mapping. This skill runs the single mapped test, parses the Surefire XML result, and updates the execution block in that Markdown case.

## Required behavior

1. Obtain the human responsible for this execution; do not use an agent, OS account, or placeholder as `ExecutedBy`.
2. Run `ppswbs-spec/.specify/scripts/powershell/run-unit-test-case.ps1 -CasePath <case> -ExecutedBy <member>`.
3. Report the exact status and preserve a failing outcome. Never change assertions, source code, or expected results merely to turn FAIL into PASS.

The runner validates the feature before and after execution. If Maven does not produce the relevant Surefire XML/testcase, it leaves the prior execution evidence unchanged and reports an actionable error. A real test failure is recorded as `FAIL`; an error is recorded as `ERROR`.
