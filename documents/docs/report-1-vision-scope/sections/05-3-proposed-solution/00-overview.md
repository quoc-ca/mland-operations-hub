# 3. Proposed Solution

Mland Operations Hub (MOH) is a bilingual, guest-accessible system for a personalised ring journey. A customer reserves a fixed-capacity group workshop session, then may choose an existing ring design, configure available ring components, upload a reference image with consent, or wait to consult staff in person.

For a reference image or custom design outside the confirmed catalogue, an external AI Vision API extracts candidate ring features. A feasibility engine then records an auto-accept, staff-review, or auto-reject decision from approved constraints, weights, and thresholds. The engine is a research prototype under validation: it does not generate designs or decide prices. Authorised staff or the owner may override a decision only with a recorded reason.

After a feasible design is available, MOH shows a price estimate from the staff-maintained component catalogue. The customer can either make the ring at the workshop or place a shop-made custom order. Shop-made orders use simple fulfilment statuses through pickup. Final invoice changes require customer consent, and staff record cash or bank-transfer payment only after the customer receives the product.

MOH sends booking and review/order notifications by email and lets guests use a tracking code. It does not integrate a payment gateway, store card data, schedule staff, manage stock, or perform detailed production planning.

![MOH operational context](assets/diagrams/moh-context.mmd)
