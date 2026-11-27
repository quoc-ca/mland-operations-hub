#### 3.2.5 Product Categories

![](assets/screens/live/51-product-categories.png)

This screen allows Staff and Managers to:

- Create a product category with a name and description.
- Find categories by part of the name and by status.
- Edit a category's name and description.
- Deactivate or reactivate a category after reviewing the products it affects.

The page opens from the **Categories** tab of the product management header. The search and status are kept when the page is shared or reopened.

**Field Description**

| Field Name | Description |
| --- | --- |
| New category: Category name | Required text input of at most 200 characters, empty by default. |
| New category: Category description | Optional text of at most 5,000 characters. |
| Create category | Creates the category as active and shows "Category \"{name}\" created." |
| Find a category | "Category name" search, empty by default, applied with **Find** or Enter. |
| Status | Options: All, Active, Inactive. Default: All. Applied as soon as it changes. |
| Category row | Name, state badge (Active or Inactive) and description. |
| Edit | Opens inline editing of the name and description with **Save** / **Cancel**. |
| Deactivate / Activate | Opens a confirmation stating how many products belong to the category, with links to them. An inactive category cannot be chosen for new products or reviews, and customers can no longer see or buy its products. |
| Empty state | "No category yet." when none exists; "No category matches the filter." when the search or status hides every category. |
| Pager | **Previous**, a page selector and **Next**. |
