### 2.9 Manage Product Categories

| Use Case ID | UC-18 | Use Case Name | Manage Product Categories |
| --- | --- | --- | --- |
| Created By |  | Created Date |  |
| Primary Actors | Staff, Manager | Secondary Actors | None |

#### Description

As an authorized catalogue maintainer, I want to find, create, update, activate, and deactivate product categories so that products are grouped cleanly without deleting history.

#### Trigger

A Staff or Manager with category-management permission opens Product Categories.

#### Preconditions

1. Actor is signed in with an active Staff or Manager account and category-management permission.

#### Postconditions

1. Category names remain unique across all categories and every change is audited.
2. Used categories are deactivated rather than deleted; history and valid holds remain unchanged.

#### Normal Flow

**Find Categories**

1. Actor opens Product Categories; System lists categories ordered by name then identity.
2. Actor selects a status filter, enters a category name keyword, and selects Find.
3. System trims the keyword, matches case-insensitive substrings with the status filter, and applies filters before paging.
4. System displays the first page of matching categories and keeps the filter criteria in the page address.

**Create or Update Category**

1. Actor fills in a new category or selects Edit on an existing category.
2. Actor enters a name (1–200 characters) and an optional description (up to 5,000 characters).
3. System trims values and verifies the name is unique across all categories regardless of case.
4. System saves the category (active by default for new ones), records an audit entry, and confirms (MSG10).

**Deactivate or Activate Category**

1. Actor selects Deactivate on an active category.
2. System displays affected products and requests confirmation (MSG29).
3. Actor confirms deactivation.
4. System deactivates the category, excludes its products from public reads and new purchases, and logs an audit entry.
5. Actor later selects Activate; System restores public visibility for eligible published, non-deleted products.

#### Alternative Flows

F4a. No category matches the filter → System displays a distinct empty message (MSG61) and keeps the filter criteria.

#### Exception Flows

A1a. Actor is unauthorized, data changed concurrently, or save failed → System rejects the request or preserves previous state (MSG01, MSG03, MSG04).

F3a. Search name exceeds 200 characters → System rejects the search and flags the name field (MSG08).

C2a. Name or description is empty or exceeds length limits → System rejects the input (MSG08).

C3a. Category name already exists → System rejects the duplicate name (MSG28).

D3a. Actor cancels deactivation → System makes no changes.

#### Business Rules

GBR-16, BR-18-01, BR-18-02, BR-18-03, BR-18-04

#### Non-Functional Requirements

—

#### Priority

—

#### Messages

MSG01, MSG03, MSG04, MSG08, MSG10, MSG28, MSG29, MSG61

#### Business Rule Definitions

| ID | Rule Definition |
| --- | --- |
| GBR-16 | Deactivating a category never cancels valid holds, changes accepted orders or removes history. |
| BR-18-01 | A category name is 1–200 characters and unique across all categories, including inactive ones, after trimming and ignoring case; the description is optional and at most 5,000 characters. |
| BR-18-02 | Before deactivation, the affected products are shown; an inactive category and its products are excluded from public reads and new purchases. |
| BR-18-03 | Reactivation restores only products that are still published, not deleted and eligible; a used category is deactivated instead of deleted, without rewriting history or valid holds. |
| BR-18-04 | The category list can be filtered by status (All, Active, Inactive) and by a trimmed, case-insensitive part of the name of at most 200 characters; both filters are applied before paging, and the order stays name then identity. Changing the filter returns to the first page. |
