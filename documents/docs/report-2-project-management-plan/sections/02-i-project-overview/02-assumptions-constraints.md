## 2. Assumptions & Constraints

::: {.docx-table width="100%" columns="15%,63%,22%" repeat-header="false"}

| No | Description | Note |
|---|---|---|
| **Assumptions** | | |
| 1 | Mland provides master data – packages, prices, branches, time slots, catalogue, and voucher rules – before acceptance testing. | Scope |
| 2 | The Mland representative attends clarification sessions and reviews the demo at the end of each iteration. | Stakeholder |
| 3 | Sandbox or free test accounts for VNPay, Firebase, Cloudinary, and GHTK remain available during development and demo. | External Interfaces |
| 4 | Production hosting, the live VNPay merchant account, and go-live are arranged by Mland after the capstone. | Scope |
| **Constraints** | | |
| 1 | The project must be delivered within the 14-week capstone term starting 15/09/2026. | Schedule |
| 2 | Five members work part-time, about 15 hours per week each, alongside other courses. | Resources |
| 3 | The system uses React + Vite (frontend), Java 21 with Spring Boot and Spring Modulith (backend), and MySQL with Flyway migrations. | Technology |
| 4 | VNPay is the only online payment gateway. A payment is confirmed only by a signed IPN or QueryDR result, never by the browser return page. | External Interfaces |
| 5 | Secrets and personal data stay out of the source repository. The system never stores card data. | Security |
| 6 | The customer interface must be available in Vietnamese and English. | Usability |

:::
