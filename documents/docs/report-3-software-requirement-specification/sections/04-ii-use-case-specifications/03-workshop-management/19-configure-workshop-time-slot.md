### 3.19 UC-50 Configure Workshop Time Slot

#### UC Name

UC-50 Configure Workshop Time Slot

#### Primary Actors

Manager

#### Secondary Actors

None

#### Description

As a Manager, I want to define the standard daily time slots of a branch so that workshop sessions can be scheduled in reusable, non-overlapping time windows.

#### Preconditions

- The actor is signed in as an active Manager.
- The branch exists in the workshop location list.

#### Normal Flow

***Configure a Workshop Time Slot***

1. The Manager opens the Khung giờ tab of workshop operations administration.
2. The Manager selects a branch.
3. The system shows the time slots of the branch with their time window and status.
4. The Manager enters a label, a start time and an end time.
5. The Manager selects Tạo khung giờ.
6. The system checks that the actor is an active Manager.
7. The system checks that the label and both times are valid and the start is before the end.
8. The system checks that the slot does not overlap another active slot of the branch.
9. The system saves the slot and records it in the audit log.
10. The system confirms that the slot has been saved (MSG329).

#### Alternative Flows

***Step 3.1 Branch Has No Slot Yet***

1. The system informs the Manager that the branch has no slot (MSG333).
2. The use case continues at step 4.

***Step 3.2 Slots Cannot Be Loaded***

1. The system informs the Manager that the data could not be loaded (MSG05).
2. The use case ends.

***Step 4.1 Manager Edits a Slot***

1. The Manager selects Sửa beside a slot.
2. The Manager changes the label or the time window.
3. The Manager selects Lưu khung giờ.
4. The use case continues at step 6.

***Step 4.2 Manager Deactivates or Reactivates a Slot***

1. The Manager selects Ngừng or Kích hoạt beside a slot.
2. The system checks that the actor is an active Manager.
3. The system checks the overlap of the slot when it is reactivated.
4. The system saves the status and records the change in the audit log.
5. The system confirms that the slot has been saved (MSG329).
6. The use case ends after the change.

***Step 4.3 Edit Changes Nothing***

1. The system informs the Manager that nothing was changed (MSG330).
2. The use case continues at step 4.

***Step 6.1 Actor Is Not an Active Manager***

1. The system refuses the action (MSG01).
2. The use case ends without a change.

***Step 7.1 Slot Data Is Not Valid***

1. The system informs the Manager which slot field is missing or invalid (MSG331).
2. The system saves nothing.
3. The use case continues at step 4.

***Step 8.1 Slot Overlaps an Active Slot***

1. The system informs the Manager of the active slot that overlaps (MSG332).
2. The system saves nothing.
3. The use case continues at step 4.

#### Postconditions

- The slot is saved with its label, time window and status.
- Existing sessions keep their times; only sessions created later use the changed slot.
- Every applied slot change is recorded in the audit log with prior and new values.

#### Business Rule

BR-171, BR-174, BR-190, BR-191, BR-192
