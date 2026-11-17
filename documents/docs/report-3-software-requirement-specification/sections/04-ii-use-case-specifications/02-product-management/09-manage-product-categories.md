### 2.9 Manage Product Categories

#### Primary Actors

Staff, Manager with category-management permission.

#### Secondary Actors

None.

#### Description

As an authorized catalogue maintainer, I want to create, rename, activate and deactivate product categories so that products are grouped correctly and categories that are no longer used can be hidden without deleting history.

This use case includes the following sub-flows:

- Create or Update Category
- Deactivate or Activate Category

#### Preconditions

1. The actor is signed in, the account is active, and the current role is Staff or Manager with category-management permission.

#### Normal Flow

**Create or Update Category**

1. The actor opens Category Management and selects New category or an existing category.
2. The actor enters a name (1–200 characters) and an optional description (up to 5,000 characters).
3. The system trims the values and checks that the name is unique across all categories, including inactive ones, ignoring case.
4. The system saves the category (new categories are active by default), records an audit entry and confirms (MSG10).

**Deactivate or Activate Category**

1. The actor selects Deactivate on an active category.
2. The system shows the products that will be affected and asks for confirmation (MSG29).
3. The actor confirms.
4. The system deactivates the category; its products are excluded from the public catalogue and new purchases. The system records an audit entry.
5. Later, the actor may select Activate. The system restores public visibility only for products that are still published, not deleted and meet all publication conditions.

#### Alternative Flows

**Create or Update Step 3 — Name already exists**

The system rejects the name (MSG28).

**Create or Update Step 2 — Invalid data**

The system rejects an empty or too long name or description (MSG08).

**Deactivate Step 3 — Actor cancels**

Nothing changes.

**Any step — Actor is not authorized, data changed or save failed**

The system rejects the request or keeps the previous state (MSG01, MSG03, MSG04).

#### Postconditions

- Category names are unique and every change is audited.
- A category that has been used is deactivated instead of deleted; history and valid holds are not changed.

#### Business Rules

BR-18-01, BR-18-02, BR-18-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-18-01 | A category name is 1–200 characters and unique across all categories, including inactive ones, after trimming and ignoring case; the description is optional and at most 5,000 characters. |
| BR-18-02 | Before deactivation, the affected products are shown; an inactive category and its products are excluded from public reads and new purchases. |
| BR-18-03 | Reactivation restores only products that are still published, not deleted and eligible; a used category is deactivated instead of deleted, without rewriting history or valid holds. |
