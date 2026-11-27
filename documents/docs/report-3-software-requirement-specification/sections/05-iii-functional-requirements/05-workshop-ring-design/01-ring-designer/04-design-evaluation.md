#### 5.1.4 Design Evaluation

![](assets/screens/live/20-design-evaluation.png)

This screen allows Members to:

- View the saved design with its ID, version and status, a read-only preview and a summary of all selected options.
- Read the advisory estimated cost, estimated weight and crafting difficulty with its note.
- See a workshop recommendation, or a too-risky warning that suggests a custom crafting request instead.
- Open saved designs, explore workshops, edit the design, open a design consultation or remove the design from the saved list.

The screen requires sign-in; a Guest sees a sign-in prompt. The evaluation is advisory and never means Staff or Manager approval. The recommendation comes from the server; if the server returns none, the page derives one from the difficulty score.

**Field Description**

| Field Name | Description |
| --- | --- |
| Header | "Design Evaluation Results", "Design ID: {id} · Version {n}" and a status badge such as "Saved" or "Archived". |
| Advisory notice | "Price estimate and difficulty score are advisory. Official payable amount adheres to your chosen workshop package." |
| Surface warning | Textured-band warning, shown when the evaluation returns one. |
| Technical estimates | "Advisory Estimated Cost" (or "Per package" when no estimate exists), noting that the package deposit does not change. Estimated weight, within the 3.0 g quota or over it with the added cost. The heading and weight texts have no English label in the app. |
| Crafting Difficulty Score | Score, level badge (Easy, Medium, Hard or Very hard), note and rules version; a very hard score adds a recommendation to change the design. |
| Configuration Summary | Material, band shape, size, thickness, width, weight, gemstones, attachments, engraving placements and laser images. |
| Preview | Read-only ring preview. |
| Saved notice | "Design saved successfully to your storage!" |
| **View Saved Designs** | Opens My Ring Designs. |
| **Proceed to Workshop Booking** | Opens the workshop list. |
| **Edit Design** | Opens Ring Configuration in edit mode; hidden for locked or archived designs. |
| **Design Consultation** | Opens Consultation Chat with this design and version as context; shown to Members. |
| **Remove from Saved** | Opens the "Remove from Saved" dialog with **Cancel** and **Confirm Archive**; confirming archives the design. Hidden for archived, locked or booking-attached designs. |
| "Recommended" | Button with the recommended workshop package (Wax Casting, Silver Clay or Signature Workshop), its reason and any note, such as Wax Casting needing two sessions on different days. The button opens that package. |
| Crafting difficulty warning | Replaces the recommendation for a too-risky design: the design is too risky to make by hand. Its button "Send a custom crafting request for this design →" opens Design Request Workspace with the design attached (no English label in the app). |
| Load error | The error message with **Restart Session**, which returns to Ring Design Entry. |
