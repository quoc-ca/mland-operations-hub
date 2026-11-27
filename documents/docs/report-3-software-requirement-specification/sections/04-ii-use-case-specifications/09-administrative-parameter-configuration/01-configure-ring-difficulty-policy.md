### 9.1 UC-71 Configure Ring Difficulty Policy

#### UC Name

UC-71 Configure Ring Difficulty Policy

#### Primary Actors

Admin

#### Secondary Actors

None

#### Description

As an Admin, I want to preview and publish a new version of the workshop-ring difficulty policy so that later ring designs are evaluated with correct scores and cutoffs.

#### Preconditions

- The actor is signed in as an active Admin.
- An effective difficulty policy version exists.

#### Normal Flow

***Configure Ring Difficulty Policy***

1. The Admin opens the ring difficulty configuration screen.
2. The system shows the effective version, its scores, cutoffs and the publication history.
3. The Admin edits option scores, coefficients and cutoffs in a local draft.
4. The system checks each edited field.
5. The Admin adds optional non-personal sample designs.
6. The Admin requests a preview of the draft.
7. The system compares before and after scores, levels and recommendations for the standard and added samples.
8. The system shows the impact on every active published ring model.
9. The system issues a preview proof valid for 15 minutes.
10. The Admin confirms publication of the draft.
11. The system revalidates the draft, the proof and every active published model.
12. The system saves the draft as a new immutable policy version and makes it effective.
13. The system confirms that the new version has been published (MSG359).

#### Alternative Flows

***Step 1.1 Actor Is Not an Active Admin***

1. The system refuses to show or change the difficulty policy (MSG01).
2. The use case ends without showing any policy value.

***Step 2.1 Policy Cannot Be Loaded***

1. The system informs the Admin that the data could not be loaded (MSG05).
2. The use case ends without showing the policy.

***Step 3.1 Admin Restores a Past Version***

1. The Admin selects a past version in the history.
2. The system loads its values into the local draft.
3. The use case continues at step 4.

***Step 4.1 A Field Is Invalid***

1. The system marks each invalid field and states the rule it breaks (MSG360).
2. The system keeps all other edits.
3. The use case continues at step 3.

***Step 8.1 Preview Finds a Blocked Model***

1. The system lists each active model that becomes unscored, invalid or too risky.
2. The system informs the Admin that the draft cannot be published (MSG361).
3. The system issues no preview proof.
4. The use case continues at step 3.

***Step 10.1 Draft Changed After the Preview***

1. The system disables publication until a new preview succeeds.
2. The use case continues at step 6.

***Step 11.1 Preview Proof Is Missing, Expired or Does Not Match***

1. The system informs the Admin that the preview must be run again (MSG362).
2. The system keeps the draft and the effective version unchanged.
3. The use case continues at step 6.

***Step 11.2 Policy or Catalogue Changed Meanwhile***

1. The system informs the Admin that the effective policy or catalogue has changed (MSG363).
2. The system keeps the draft and the effective version unchanged.
3. The Admin reloads the latest version.
4. The use case continues at step 3.

***Step 11.3 A Model Became Blocked Before Publication***

1. The system rejects the whole draft and lists the blocked models (MSG361).
2. The system keeps the previous version effective.
3. The use case continues at step 3.

***Step 12.1 Publication Result Is Not Confirmed***

1. The system informs the Admin that the result could not be confirmed (MSG04).
2. The system keeps the draft for a retry with the same command.
3. The use case continues at step 10.

***Step 12.2 Same Publication Is Sent Again***

1. The system returns the result of the earlier identical publication.
2. The system creates no second version.
3. The use case continues at step 13.

#### Postconditions

- A new immutable policy version is effective, and older versions stay in the history.
- Only ring design evaluations made after publication use the new version.
- Saved designs are not re-scored.
- The publication records the Admin and the publication time.

#### Business Rule

BR-218, BR-219, BR-220, BR-221, BR-222
