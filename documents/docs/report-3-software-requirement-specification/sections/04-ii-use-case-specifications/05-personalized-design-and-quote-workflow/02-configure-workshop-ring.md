### 5.2 Configure Workshop Ring

#### Primary Actors

- Member

#### Secondary Actors

- None

#### Description

This use case allows a Member to configure a workshop ring from a published model, a blank ring or an own saved design, then save and evaluate it. Price and difficulty are advisory. The design is not attached to a workshop booking or ticket.

#### Preconditions

1. The Member is signed in and the account is active.
2. The catalogue options of the workshop package are available.
3. When editing, the design belongs to the Member and is not archived.

#### Normal Flow

**Configure Workshop Ring**

1. The system displays the options supplied by the server for the starting point.
2. The Member chooses material, shape, ring size, dimensions, gemstones, components, attachments and engraving.
3. The system shows a local preview marked as a preview and flags invalid choices.
4. The Member selects save and evaluate.
5. The system validates ownership, version, options, package compatibility and limits.
6. The system stores a new immutable design version and calculates the advisory estimated price and difficulty.
7. The system displays the saved version, its summary, warnings and a workshop recommendation.
8. The Member may edit the design, explore workshops, open a design consultation or attach the design to a custom design request.

#### Alternative Flows

**Step 1 — Actor is a Guest**

The Guest sees the entry point and public options; a protected action prompts sign-in and creates nothing.

**Step 5 — Invalid, inactive or incompatible option, or limit exceeded**

The system rejects the save, identifies the problem and keeps the Member's input.

**Step 5 — Design changed elsewhere**

The system returns a version conflict and asks the Member to reload.

**Step 6 — Rule service unavailable**

The system saves no new version and asks the Member to retry.

**Step 8 — Member archives or restores the design**

Archiving hides the design without erasing its history; the Member may restore it.

#### Postconditions

- A saved design version exists and belongs to the Member; earlier versions are retained.
- No booking, ticket, payment, quotation, custom order or custom design request is created automatically.

#### Business Rules

- BR-33-01
- BR-33-02
- BR-33-03

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-33-01 | Only a signed-in Member may configure, save, evaluate, archive or restore a design, and only an own design. Options come from the server. |
| BR-33-02 | The system rejects invalid or incompatible options. A design has at most 10 gemstones, 2 text engravings, 2 symbol engravings and 2 laser images. |
| BR-33-03 | Estimated price, difficulty and the workshop recommendation are advisory; they change no package price, payment or quotation and hide no workshop. |
