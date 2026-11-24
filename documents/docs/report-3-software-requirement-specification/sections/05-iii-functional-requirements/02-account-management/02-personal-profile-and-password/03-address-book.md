#### 2.2.3 Address Book

This screen allows Members, Staff, Managers and Admins to:

- See their saved delivery addresses, with the default address marked.
- Add a new address or edit an existing one in a form that uses the official province and ward/commune lists.
- Set another address as the default.
- Delete an address after a confirmation.

A user can save at most 10 addresses. The first address becomes the default automatically. The default address cannot be deleted while other addresses exist. The Carrier cannot open this screen.

| Field Name | Description |
| --- | --- |
| Saved count | "Saved: n/10 addresses". At 10 addresses, **+ Add new address** is disabled and a notice asks the user to delete or edit an address. |
| Address card | Recipient name, phone number, full address, the **Default** badge and the address type (Home or Office). |
| Edit / Delete / Set as default | **Edit** opens the address form; **Delete** is disabled for the default address while others exist; **Set as default** is shown on non-default addresses. |
| Confirm address deletion | Dialog naming the recipient and address, warning that the address is removed permanently, with **Cancel** and **Confirm deletion**. |
| Recipient full name | Required, up to 255 characters; the next fields unlock one after another as each is valid. |
| Phone number | Required; a 10-digit Vietnamese mobile number starting with 03, 05, 07, 08 or 09 (+84, spaces, dots and dashes are normalised). |
| Province / City and Ward / Commune / Special zone | Required drop-downs; the ward list has a quick filter, and changing the province clears the ward and street address. |
| Street address | Required house number, building and street. |
| Address type / Set as default delivery address | Home or Office, and a default checkbox that is fixed for the first address. |
| Empty state | "You have no delivery addresses yet" with **Add your first address**. |
