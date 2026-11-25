# Feature-004 Human Review Requirements Checklist

- [ ] No Feature-004 API invokes an AI provider.
- [ ] An authenticated Member can submit a description-only, image-only or combined request.
- [ ] A request accepts at most five images.
- [ ] Image quality is shown immediately after upload and revalidated by backend.
- [ ] Too-blurry images block submit.
- [ ] Guest request creation, upload and tracking are denied with a sign-in prompt.
- [ ] New requests start at `NEED_REVIEW`.
- [ ] Staff and Manager have equal accept/reject authority.
- [ ] Explanation is mandatory for both decisions.
- [ ] Terminal decisions cannot be duplicated or overwritten concurrently.
- [ ] Customer sees status and decision explanation.
- [ ] Admin cannot perform business review.
- [ ] No booking/payment/Feature-012 mutation is introduced.
- [ ] Old AI contract and E2E evidence are marked deprecated.
