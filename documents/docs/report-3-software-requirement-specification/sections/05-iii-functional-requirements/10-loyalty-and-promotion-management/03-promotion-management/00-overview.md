### 10.3 Promotion Management

**Input checks while typing.** The Promotion Form, programme editing and Point Policy check a field as soon as its value changes, using the same rules as on save:

1. An invalid field shows its error at once; correcting it removes the error at once.
2. Before the first save, only fields the user has edited show errors.
3. Related fields are checked together; changing the start rechecks an end already entered.
4. After the first save, every field is rechecked on each change.
5. The Point Policy form starts without errors each time it is reopened.

Target selection errors and "nothing has changed" on the edit form still appear only on save.
