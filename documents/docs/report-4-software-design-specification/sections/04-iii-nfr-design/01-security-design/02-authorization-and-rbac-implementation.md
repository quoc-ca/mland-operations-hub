### 1.2 Authorization & RBAC Implementation

::: {.docx-table width="100%" columns="30%,70%" repeat-header="false"}

|Aspect|Decision|
|---|---|
|Role model|Each Account has one fixed role: `MEMBER`, `STAFF`, `MANAGER`, `CARRIER`, or `ADMIN`. V1 has no configurable roles or permissions.|
|Request admission|The shared identity gate admits declared public routes. Protected routes require a valid bearer token and a current Mland Account. Unresolved or inactive accounts are denied.|
|Business action|The owning feature checks the current role, record ownership, action, and applicable status. Browser route guards hide unavailable pages but do not authorize API calls.|
|Location scope|Staff actions use current assignments to active Workshop Locations. If scope cannot be checked, the action stops with a retryable error. A Manager has global scope only for actions that the owning feature allows.|
|Restricted views|Member data is owner-scoped. The executive dashboard is Manager-only; Admin account administration does not grant access to it. CARRIER access is limited to its approved account and delivery functions.|
|Invitation operations|Only Admin creates or resends internal invitations. Admin alone may retrieve current unexpired mail content after two definite SMTP failures; an uncertain outcome requires reconciliation. Generic notification resend and cancel actions reject invitations.|
|Audit and denial|Sensitive actions and denials record safe actor, action, target, outcome, and time. Audit records omit raw tokens, contact data, and unnecessary personal details.|

:::
