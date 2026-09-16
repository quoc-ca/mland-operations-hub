# 3. Proposed Solution

Mland Operations Hub (MOH) is a bilingual, guest-accessible system for a personalised ring journey. Under the 16 September stakeholder proposal, a customer selects one of Mland's two workshop locations and reserves a group workshop session, then may choose an existing ring design, configure available ring components, upload a reference image with consent, or wait to consult staff in person. Location selection, the stated three-session operating day, and location-specific capacity/capability remain stakeholder input pending group approval.

For a reference image or custom design outside the confirmed catalogue, an external AI Vision API extracts candidate ring features. A feasibility engine then records an auto-accept, staff-review, or auto-reject decision from approved constraints, weights, and thresholds. The engine is a research prototype under validation: it does not generate designs or decide prices. Authorised staff or the owner may override a decision only with a recorded reason.

After a feasible design is available, MOH shows a price estimate from the staff-maintained component catalogue. The customer can either make the ring at the workshop or place a shop-made custom order. Shop-made orders use simple fulfilment statuses through pickup. At check-in, staff confirm the people actually participating in making the product; the proposed attendance rule adds 100,000 VND for each unbooked additional participant. Final invoice changes require customer consent, and staff record cash or bank-transfer payment only after the customer receives the product.

Some workshop products may require more than one session. In the proposed continuation flow, staff record shop custody of the work-in-progress product and create a later booking linked to it. MOH does not automatically reserve a next session or allow the guest to create a continuation booking without staff action.

MOH sends booking and review/order notifications by email and lets guests use a tracking code. An owner/manager Google Reviews dashboard is a research-gated candidate: it must not automatically calculate employee pay. Any review used in a bonus decision needs manual staff attribution, reason, approver, and audit evidence. MOH does not integrate a payment gateway, store card data, schedule staff, manage stock, or perform detailed production planning.

![MOH operational context](assets/diagrams/moh-context.mmd)
