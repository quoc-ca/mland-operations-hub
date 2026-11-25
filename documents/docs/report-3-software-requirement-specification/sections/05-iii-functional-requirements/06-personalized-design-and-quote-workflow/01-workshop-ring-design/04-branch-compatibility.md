#### 6.1.4 Branch Compatibility (Retired)

This screen allows Members to:

- See that the selected workshop branch does not support some materials or techniques of the current ring design.
- Read the reason supplied with the warning, or a default reason when none is given.
- Open the design in Ring Configuration to adjust it.
- Go to workshop booking to choose another branch for the same package and design.

> **Retired:** Feature-012 no longer validates or attaches a design to a selected branch. Branch compatibility belongs to the independent workshop-booking contract. This fragment is retained only for historical traceability and must not be implemented as a Feature-012 screen.

| Field Name | Description |
| --- | --- |
| "Thiết kế cần điều chỉnh cho chi nhánh" | Warning title of the recovery card. |
| Description | States that the chosen branch does not yet support some materials or techniques of the ring. |
| "Lý do từ hệ thống" | Reason passed in the page address; defaults to a general message about unsupported materials. |
| **Điều chỉnh thiết kế** | Opens Ring Configuration in edit mode for the design. |
| **Chọn chi nhánh khác** | Opens workshop booking with the package and design references. |
