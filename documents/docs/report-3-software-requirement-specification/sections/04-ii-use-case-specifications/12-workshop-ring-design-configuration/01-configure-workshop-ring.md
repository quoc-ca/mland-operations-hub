### 12.1 UC-17 Configure Workshop Ring

#### UC Name

UC-17 Configure Workshop Ring

#### Primary Actors

Member

#### Secondary Actors

None

#### Description

As a Member, I want to configure a workshop ring from a published model or a blank design and save it so that I receive an estimate and an advisory workshop recommendation.

#### Preconditions

- The Member is signed in with an active account.
- The ring options catalogue has active options with published difficulty scores.

#### Normal Flow

***Configure Workshop Ring***

1. The Member requests to design a ring from a workshop package page.
2. The system shows the published ring models and a blank design option.
3. The Member selects a published ring model.
4. The system shows the configurator with the model's default material, shape, components and attachments.
5. The Member selects the material, shape, ring size and band dimensions.
6. The Member adds gemstones, components and attachments with their placements.
7. The Member adds text, symbol or laser-image engravings.
8. The system updates the local preview and the indicative estimate.
9. The Member requests to save and evaluate the design.
10. The system validates the options and the configuration limits.
11. The system calculates the estimated price, difficulty score and level under the effective policy.
12. The system saves a new immutable design version with its rules version.
13. The system determines the advisory workshop recommendation.
14. The system shows the saved version, its summary and the recommendation (MSG382).

#### Alternative Flows

***Step 1.1 Member Edits a Saved Design***

1. The Member opens one of their saved designs.
2. The system loads the latest version into the configurator.
3. The use case continues at step 5.

***Step 1.2 Saved Design Belongs to Another Member***

1. The system informs the Member that they do not have permission to open the design (MSG01).
2. The use case ends.

***Step 2.1 Options Cannot Be Loaded***

1. The system informs the actor that the data could not be loaded (MSG05).
2. The use case ends.

***Step 3.1 Member Starts From a Blank Design***

1. The Member selects the blank design.
2. The system shows the configurator with default Silver 925, a classic round shape, size 8 and no components.
3. The use case continues at step 5.

***Step 5.1 Option Is Waiting for a Difficulty Score***

1. The system shows the option as disabled with the reason (MSG383).
2. The use case continues at step 5.

***Step 6.1 Gemstone Limit Is Reached***

1. The system informs the Member that the gemstone limit is reached (MSG384).
2. The system blocks adding further gemstones.
3. The use case continues at step 6.

***Step 7.1 Engraving Limit Is Reached***

1. The system informs the Member that the engraving limit is reached (MSG385).
2. The system blocks adding further engravings.
3. The use case continues at step 7.

***Step 7.2 Outer-Band Engraving With Gemstones***

1. The system informs the Member that outer-band engraving is not possible with gemstones (MSG386).
2. The use case continues at step 7.

***Step 9.1 Guest Requests to Save***

1. The Guest has changed the parameters in a local, unsaved preview.
2. The system opens the sign-in page with a return link to the configurator.
3. The system sends no design request and saves nothing.
4. The use case ends.

***Step 10.1 Option Is Invalid or a Limit Is Exceeded***

1. The system informs the Member that options are invalid or unavailable, with the reasons (MSG387).
2. The system creates no design version.
3. The use case continues at step 5.

***Step 10.2 Design Is Archived***

1. The system informs the Member that the design cannot be changed (MSG388).
2. The system creates no design version.
3. The use case ends.

***Step 11.1 Rule Evaluation Is Unavailable***

1. The system informs the Member that rule evaluation is temporarily unavailable (MSG389).
2. The system keeps the Member's form.
3. The use case continues at step 9.

***Step 12.1 Design Was Changed Meanwhile***

1. The system informs the Member that the design was changed by someone else (MSG03).
2. The system reloads the latest version.
3. The use case continues at step 5.

***Step 13.1 Design Is Too Risky***

1. The system recommends no workshop and explains the consultation or custom-design path (MSG390).
2. The system keeps general workshop discovery available.
3. The use case continues at step 14.

***Step 13.2 Wax Casting Is Recommended***

1. The system recommends Wax Casting and explains its two sessions at least two foundry working dates apart.
2. The use case continues at step 14.

***Step 13.3 Silver Clay Is Recommended for a Silver 925 Design***

1. The system recommends Silver Clay and discloses that this workshop uses Silver 999.
2. The system keeps the saved Silver 925 material unchanged.
3. The use case continues at step 14.

***Step 13.4 No Compatible Workshop Package***

1. The system informs the Member that no workshop package matches the material (MSG391).
2. The system offers consultation and general workshop discovery.
3. The use case continues at step 14.

#### Postconditions

- A new immutable version of the Member's design is saved with its estimate, score, level and rules version.
- Earlier versions keep their original evaluation.
- No booking, ticket, payment, quote or custom order is created or changed.

#### Business Rule

BR-235, BR-236, BR-237, BR-238, BR-239, BR-240
