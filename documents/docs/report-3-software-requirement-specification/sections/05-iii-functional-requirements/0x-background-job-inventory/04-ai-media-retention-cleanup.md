### x.4 AI and Reference Media Retention Cleanup Job (`AI_MEDIA_RETENTION_CLEANUP`)

*Trigger*: Daily scheduled run, with an immediate cleanup request when an image-analysis request is rejected, withdrawn, or completed.

*Purpose*: Enforce the approved retention rules for Gemini text logs, AI image input, and custom-manufacturing reference images.

*Processing steps*:

    1. Find Gemini text prompts/responses older than 30 days and AI image inputs that are no longer required after processing.
    2. Find rejected or withdrawn custom requests and independently classified images eligible for immediate deletion.
    3. Delete the matching object-storage files and application records, recording only the minimum cleanup audit evidence.
    4. Keep eligible custom-manufacturing reference images until pickup or GHTK handoff.

*Failure behavior*:

|Scenario|System behavior|
|---|---|
|Object-storage deletion fails|Keep the cleanup item pending, retry it, and do not claim deletion until the provider confirms success.|
|A record is still attached to an active order|Skip deletion and recalculate eligibility on the next run.|
|The same cleanup item is processed twice|Treat the missing object/record as an idempotent success and retain the cleanup audit result.|
