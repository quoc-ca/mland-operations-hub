## 1. Security Design

The backend checks every protected request. A shared identity gate verifies the caller, then the owning feature checks the requested action, record, and location. Public pages and limited booking links have separate, explicit access rules. Browser navigation does not replace server authorization.
