### 9.2 UC-73 View Technical Audit Records

#### UC Name

UC-73 View Technical Audit Records

#### Primary Actors

Admin, Manager

#### Secondary Actors

None

#### Description

As an Admin or Manager, I want to view masked technical records of reviews and ring designs so that I can audit system behaviour without seeing customer data or making business decisions.

#### Preconditions

- The actor is signed in as an Admin or Manager.
- The actor knows the reference of the review or ring design to audit.

#### Normal Flow

***View Technical Audit Record***

1. The actor requests the technical record of a review by its reference.
2. The system checks the actor's role.
3. The system finds the review.
4. The system shows the review target, rating, status, image count, text length, version, reply flag and timestamps.
5. The system hides the customer identity, contact data and review text.
6. The system confirms that the technical record has been retrieved.

#### Alternative Flows

***Step 1.1 Actor Audits a Ring Design***

1. The actor requests the technical record of a ring design by its reference.
2. The system finds the latest version of the ring design.
3. The system shows the package, design version, type, status, material, shape, size, difficulty score, rules version, lock flag and timestamps.
4. The system hides the owner, the estimated price and the private engraving content.
5. The use case ends after the record is shown.

***Step 2.1 Actor Is Not an Admin or Manager***

1. The system refuses access to the technical record (MSG01).
2. The use case ends without showing any record.

***Step 3.1 Record Does Not Exist***

1. The system informs the actor that no record matches the reference (MSG364).
2. The use case ends without showing any record.

#### Postconditions

- No review, ring design or business decision is created or changed.
- No customer identity, contact data, review text, price or engraving content is disclosed.

#### Business Rule

BR-223
