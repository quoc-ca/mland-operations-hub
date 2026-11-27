#### 4.6.4 Package Availability

![](assets/screens/live/78-package-availability.png)

This screen allows Managers to:

- See a matrix of workshop packages against locations, showing where each package is sold.
- Turn selling of a package at a location on or off with one cell.
- See the result of each change, or a conflict when the cell was changed elsewhere.

Turning a cell off only stops new bookings at that location; existing bookings stay unchanged. Only an active Manager can view and change the matrix; other users see an access-denied panel. The screen sets availability per location only, not per date.

**Field Description**

| Field Name | Description |
| --- | --- |
| Intro and note | "A ticked cell = the package is on sale at that location." Turning a cell off only stops new bookings. |
| **Packages on sale by location** / **Reload** | Matrix with locations as rows and packages as columns ("Location \\ Package"); **Reload** reads it again. |
| Cell | Ticked when the package is sold at the location (**Active**); unticked otherwise (**Inactive**). |
| Saving | **Saving…** banner; the cell being saved is disabled while other cells stay usable. |
| Success banner | **Sales turned on** or **Sales turned off** for the package and location; hides after a few seconds. |
| Conflict banner | **This cell was just changed elsewhere**: the latest value is reloaded, with **Reload matrix**. |
| Error banner | **The change could not be saved**: the cell keeps its old value, with a reference code, **Try again** when the error allows it, and **Reload matrix**. |
| Empty state | **There is no package or location to set up yet**, with **Go to Workshop packages** and **Reload**. |
| Access denied | **You are not allowed to view this page** with **Back to the main page**. |
