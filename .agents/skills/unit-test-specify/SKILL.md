---
name: unit-test-specify
description: Create one Java JUnit unit-test method and one traceable Markdown evidence case for a PPSWBS feature.
metadata:
  short-description: Create Java unit tests with one Markdown case each
---

# Java Unit-Test Specify

Use for PPSWBS backend unit testing only. Create exactly one JUnit 5 unit-test method and exactly one corresponding Markdown case per invocation. Do not use for integration, Spring-context, frontend, system, or manual tests.

## Required discovery

Read the feature's `spec.md` and/or `tasks.md`, the source class, existing test conventions, and `ppswbs-spec/.specify/metadata-policy.md`. Obtain the responsible team member before creating or updating the case; never infer a person from Git, the OS, or the agent.

## Output contract

- Test source: `ppswbs_backend/src/test/java/<package>/<SourceClass>Test.java`.
- Case: `ppswbs-spec/features/<feature>/test-results/UTC-<feature-number>-<NNN>--<lowercase-slug>.md`.
- The case front matter must map `source_class#source_method` and `test_class#test_method`, with `test_level: unit`, `test_case_type: N|A|B`, and `source_ref: spec.md#<ID>` or `tasks.md#<ID>`.
- Use the format in [case template](references/case-template.md). Keep every field concrete; do not write expected results merely to make a generated assertion pass.

## Unit-test boundary

Use JUnit Jupiter and Mockito when collaborator isolation is needed. Test classes end with `Test`. Do not use `@SpringBootTest`, `@AutoConfigureMockMvc`, `MockMvc`, `@TestPropertySource`, databases, HTTP, or an `*IT` class. Escalate instead of mislabeling an integration concern as unit coverage.

Run `ppswbs-spec/.specify/scripts/powershell/validate-unit-test-cases.ps1 -FeatureDirectory <feature>` after drafting. Do not execute Maven or update execution statistics; use `$unit-test-execute` after the case and test source are valid.
