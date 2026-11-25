## 2. Package Diagram

The system has two applications: a React/Vite single-page front-end (`ppswbs_frontend`) and a Java 21/Spring Boot back-end (`ppswbs_backend`). Frontend capabilities live under `src/features`; the application shell and shared technical foundations live under `src/app` and `src/shared`. Backend business modules live under `features` and follow Spring Modulith boundaries with controller, DTO, service, facade, DAO and entity responsibilities. The legacy `designreview` package remains a separate compatibility layout and must not be treated as the owner of the current Feature-004 human-review contract. Cross-module access uses exported facades or approved events; private controllers, DTOs, entities and repositories are not imported across modules.

### 2.1 Front-end Package Diagram

{{r4-pd-frontend width=100% align=center}}

#### Package descriptions

::: {.docx-table width="100%" columns="6%,22%,72%" repeat-header="false"}

|No|Package|Description|
|---|---|---|
|01|`main`|Entry file of the front-end (`src/main.jsx`). It mounts the React application into the root element of the HTML page and starts it in strict mode.|
|02|`app`|Holds the application shell: the root `App` component, the shared page layout, the router that joins the routes of all features, and the "not found" and "not implemented" pages.|
|03|`routes`|Declares the URL paths of each feature (`features/<feature>/routes`) and the screen shown for each path. Routes are wrapped by the session and role guards so that each user only reaches the screens of their own role (Member, Staff, Manager, Admin, Carrier).|
|04|`ui`|Contains the screens and reusable interface components of each feature, grouped by user role, for example the cart, checkout, order history and carrier screens of retail ordering, or the Manager dashboard. Screen texts are kept in message files so they can be shown in Vietnamese or English.|
|05|`application`|Holds the React hooks that connect the screens to the data layer. They call the infrastructure clients, keep loading and error states, and give the screens ready-to-use data and actions.|
|06|`domain`|Defines the business rules that run on the client without depending on React or the network, such as voucher and discount calculation for product prices, ring design draft rules and chat message validation.|
|07|`infrastructure`|Implements the API clients of each feature. They call the back-end REST endpoints, send request bodies and turn the JSON responses into objects used by the application layer.|
|08|`api`|Shared HTTP client (`shared/api`). It sends every request to the back-end with the Firebase ID token of the signed-in user, and reads error responses in one common way.|
|09|`auth`|Manages user sign-in and session (`shared/auth`) with Firebase Authentication: the authentication provider, the session and route access guards, the current user role and the home page of each role.|
|10|`i18n`|Provides language support (`shared/i18n`) for Vietnamese and English: the current language, text translation, and number, money and date formatting.|
|11|`nav`|Builds the navigation menu of the operations shell (`shared/nav`) for each role and the badge counters shown on menu items.|
|12|`shared/ui`|Shared interface parts used by every feature: the operations shell and top bar, loading, notice and pagination components, the cart tabs and the hooks that keep list filters in the URL.|

:::

### 2.2 Back-end Package Diagram

{{r4-pd-backend width=100% align=center}}

#### Package descriptions

::: {.docx-table width="100%" columns="6%,28%,66%" repeat-header="false"}

|No|Package|Description|
|---|---|---|
|01|`PpswbsBackendApplication`|The main class and starting point of the Spring Boot application.|
|02|`configuration`|Contains application-wide settings and configurations: security filter chains, Firebase ID token verification, scheduled jobs and the time zone (Asia/Ho_Chi_Minh).|
|03|`controller`|Handles incoming REST API requests from the front-end, checks the role of the caller and sends back responses.|
|04|`dto`|Defines the structure of data sent and received through API requests (request and response records).|
|05|`service`|Contains the core business logic and rules of each feature, and controls the transactions that read and write data.|
|06|`facade`|Declares the interfaces and views that a module exports to other modules. Other modules may only call a module through its facade; this rule is checked by Spring Modulith architecture tests.|
|07|`dao`|Performs database operations (save, find, update, delete) using Spring Data JPA repositories.|
|08|`entity`|Represents database tables as Java objects (JPA entities).|
|09|`common`|Holds code shared by all modules: the correlation ID filter, the common error response, and shared events and ports for booking, payment, notification, settlement and workshop.|

:::
