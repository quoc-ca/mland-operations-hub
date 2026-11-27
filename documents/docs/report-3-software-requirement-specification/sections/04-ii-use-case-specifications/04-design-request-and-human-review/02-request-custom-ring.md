### 4.2 UC-18 Request Custom Ring

#### UC Name

UC-18 Request Custom Ring

#### Primary Actors

Member

#### Secondary Actors

None

#### Description

As a Member, I want to send my ring idea with a description, reference images or a saved ring design so that Mland can review it and quote a custom ring.

#### Preconditions

- The Member is signed in with an active Member account.
- Any saved ring design to attach belongs to the Member and is not archived.

#### Normal Flow

***Request Custom Ring***

1. The Member opens the custom design request page.
2. The system shows the description box, the image area, the saved designs and the image consent card.
3. The Member enters a description of the ring idea.
4. The Member adds reference images.
5. The system checks each image in the browser and shows its quality result.
6. The Member reads and accepts the image consent terms.
7. The Member selects a saved ring design to attach.
8. The Member submits the request.
9. The system checks the content, the images, the consent and the attached design.
10. The system stores the request in the Need review status with the consent record.
11. The system stores a fixed snapshot of the attached ring design.
12. The system shows the request details with its status and next action.
13. The system confirms that the request has been sent for Staff review (MSG273).

#### Alternative Flows

***Step 1.1 Guest Opens the Request Page***

1. The system shows the feature description and a sign-in button (MSG266).
2. The system sends no request data for the Guest.
3. The use case ends without a request.

***Step 3.1 Member Sends Only Some Inputs***

1. The Member skips the description, the images or the saved design.
2. The use case continues at step 8 when at least one input is given.

***Step 5.1 Image Breaks the File Rules***

1. The system rejects the image and states the broken rule (MSG268).
2. The use case continues at step 4.

***Step 5.2 Image Is Too Blurry***

1. The system marks the image as too blurry (MSG269).
2. The system blocks submission until the image is removed.
3. The use case continues at step 4.

***Step 8.1 Images Are Added Without Consent***

1. The system informs the Member that consent is required (MSG270).
2. The use case continues at step 6.

***Step 9.1 Request Has No Valid Content***

1. The system informs the Member that a description, image or saved design is needed (MSG267).
2. The system saves nothing.
3. The use case continues at step 3.

***Step 9.2 Server Rejects an Image***

1. The system rejects the request and names the image that failed (MSG268).
2. The system saves nothing.
3. The use case continues at step 4.

***Step 9.3 Too Many Image Submissions***

1. The system informs the Member to wait before sending images again (MSG271).
2. The system saves nothing.
3. The use case ends without a request.

***Step 9.4 Attached Design Cannot Be Used***

1. The system informs the Member that the saved design cannot be attached (MSG272).
2. The system saves nothing.
3. The use case continues at step 7.

***Step 13.1 Staff Requests Clarification***

1. The Member opens the request in My custom requests.
2. The system shows the reviewer's explanation and the clarification request.
3. The Member enters a clarification response or updated description.
4. The Member submits the revision.
5. The system checks ownership, the clarification status and the request version.
6. The system stores the revision and returns the request to the Need review status.
7. The use case ends with the request waiting for review again.

***Step 13.2 Revision Uses an Outdated Version***

1. The system informs the Member that the request has changed (MSG03).
2. The system does not store the revision.
3. The use case continues at step 13.1.1.

***Step 13.3 Request Is Accepted***

1. The Member opens the request.
2. The system shows the acceptance, the explanation and the quote once the Manager approves it.
3. The use case ends; payment continues in UC-19 Pay Custom Order Quote.

***Step 13.4 Request Is Rejected***

1. The Member opens the request.
2. The system shows the rejection and the reviewer's explanation.
3. The use case ends without a custom order.

#### Postconditions

- The design request exists in the Need review status and belongs to the Member.
- The reference images, the consent record and the design snapshot are stored.
- No booking, order, quote or payment record is created.

#### Business Rule

BR-05, BR-147, BR-148, BR-149
