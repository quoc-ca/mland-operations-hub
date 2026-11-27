#### 6.1.1 Design Request Workspace

![](assets/screens/live/39-design-request-workspace.png)

This screen allows authenticated Members to:

- Describe the ring they want made, upload up to five reference images, and optionally attach one of their saved workshop ring designs.
- See per-image quality feedback and replace images that are blurry or invalid before sending.
- Give consent to image processing whenever at least one image is attached.
- Submit the request for human review by Staff or a Manager.

The screen requires sign-in; a Guest sees only a sign-in prompt. A request needs at least a description, an attached design or one image. After sending, the Member is taken to Design Request Details with a success notice. The server rechecks ownership of the attached design and stores a snapshot of it.

On the screen, they can also:

- Enlarge a selected image before sending.
- Open the ring designer in a new tab when they have no saved design.

**Field Description**

| Field Name | Description |
| --- | --- |
| Title and subtitle | "Bringing Your Ideas to Life" with "Describe the ring you want us to craft, and we will evaluate and propose the best pricing for your bespoke design." |
| View my requests | Opens the craft requests tab of My Ring Designs (no English label in the app). |
| Attached ring design | Optional list of the Member's saved designs, each summarised by code, material, shape, size, stones, engraving and version. Default: "-- No design attached --". A chosen design shows a details card and a remove-attachment button. Without saved designs: "You have no saved designs yet." with "Design a ring now ↗". These texts have no English label in the app. |
| Design idea description | Optional text of at most 4,000 characters with a "{count}/4,000 characters" counter. |
| Reference images | Click or drag-and-drop area for JPEG, PNG or WEBP, at most 5 MB and 4096 × 4096 px each, at most 5 images. Each image shows "Checking image sharpness...", "Too blurry ({score})", "Image error" or "Passed", with view and remove buttons. Area texts have no English label in the app. |
| Blur warning | "Image is too blurry! Please select a sharper image for staff review." with advice to replace it. |
| Image Use Terms & Crafting Copyright Disclaimer | Shown when images are attached: a summary, **📜 Read & Accept Terms** (or **Review Terms**), a required check box and "✓ Terms Accepted" once accepted. Ticking the box also opens the terms dialog. |
| Terms dialog | "Custom Crafting Image Use & Copyright Liability Terms". **Accept** and **Decline** work only after scrolling to the end; **Decline** returns to the previous page. The terms body has no English label in the app. |
| **Submit design request** | Sends the request. Disabled while sending, while images are checked ("Checking images…"), while a blurry or invalid image remains ("Please replace the blurry images") or without consent. These state texts have no English label in the app. |
| Validation and error messages | Empty request, description too long, too many images, image too large, blurry or invalid image, missing consent, and server messages such as the image upload rate limit. |
