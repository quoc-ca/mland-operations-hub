#### 4.6.1 Workshop Packages

![](assets/screens/live/75-workshop-packages.png)

This screen allows Managers to:

- See all workshop packages with their state, price, ticket range and required sessions.
- Create a package as a draft, with an optional cover image, and publish it when it is ready.
- Edit a package that is not retired; a change applies only to bookings made afterwards.
- Retire a published package, which stops new bookings and keeps existing bookings unchanged.

Staff can open the page, but it is not in their menu; creating, editing, publishing and retiring are Manager commands checked by the server.

**Field Description**

| Field Name | Description |
| --- | --- |
| **Create package** | Header button; opens an empty form to create a draft package. |
| **All packages** | Package cards with the record count. Each card shows the state (**Draft**, **Published** or **Retired**), name, package id, description, **Price**, **Tickets** (min–max) and **Sessions**. |
| **Edit** / **Retire** | **Edit** on any package that is not retired; **Retire** on a published package, after confirming "Retire package?". |
| Empty state | "No workshop packages found." with **Create the first package**. |
| **Name** | Required, up to 180 characters. |
| **Public price (VND)** | Public price per participant; must be greater than 0. |
| **Minimum tickets** / **Maximum tickets** | Ticket range per booking; default 1 and 1. The maximum must be at least the minimum. |
| **Required sessions** | Number of sessions a booking must choose; default 1, at least one. |
| **Minimum same-day slot gap** / **Foundry working days between sessions** | Non-negative whole numbers; default 0. |
| **Require the same location** | Whether all sessions of a booking must be at one location; ticked by default. |
| **Customer-safe description** | Required, up to 1,000 characters. |
| **Cover image (optional)** | JPG, PNG or WebP, up to 5 MB, with a preview, **Upload again** after a failure and **Remove image**. |
| **Create draft** / **Save changes** / **Cancel** | Creates the draft, saves the edit, or closes the form. |
| **Draft ready** / **Publish draft** | Shown for a draft being edited; publishes it so customers can find the package. |
