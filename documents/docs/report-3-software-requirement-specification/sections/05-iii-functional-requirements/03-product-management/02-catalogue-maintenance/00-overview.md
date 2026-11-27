### 3.2 Catalogue Maintenance

**Input checks while typing.** Every input form on these screens checks a field as soon as its value changes, using the same rules as on save:

1. An invalid field shows its error at once; correcting it removes the error at once.
2. Before the first save, only fields the user has edited show errors, including the price or quantity of a newly ticked size.
3. After the first save, every field is rechecked on each change.
4. A bulk price or quantity field shows an error only when filled in wrongly; an empty one is reported only when applied.
5. A reopened form, after Edit or Cancel, starts without errors.

Checks that only the save can decide, such as an image still uploading, still appear on save.
