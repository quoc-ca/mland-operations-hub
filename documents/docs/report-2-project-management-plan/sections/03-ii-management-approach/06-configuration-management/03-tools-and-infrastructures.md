```{=openxml}
<w:p><w:r><w:br w:type="page"/></w:r></w:p>
```

### 6.3 Tools & Infrastructures

| Category | Tools / Infrastructure | Status |
|---|---|---|
| Documentation source | Markdown fragments and CSV trackers in the Git repository | In use for project documentation |
| Document generation | Python tooling, Pandoc, and the shared `reference.docx` template | In use for local/CI document builds |
| Version control and review | Git and GitHub | In use for documentation and proposed source review |
| Project tracking | GitHub Issues and repository trackers | In use for open work and project evidence |
| Publishing and review | Google Docs and Google Sheets through the configured synchronization workflow | In use for mapped document review surfaces |
| Diagramming | Figma for UI design; Draw.io/PlantUML source may be used to prepare diagrams, then published as Drive image assets | In use for design preparation; final report images follow the document publishing workflow |
| Product technology and database | React + Vite frontend; Java 21/Spring Boot/Spring Modulith backend; JPA/Spring Data; MySQL; Flyway | Approved platform baseline; feature-specific readiness still varies |
| IDEs / editors | Team-selected IDE/editor with Git integration | In use; no single IDE is imposed |
| Product deployment infrastructure | Local development and demo environments; production hosting and deployment topology are not approved | TBD — deployment decision required |

The documentation tools and the product stack are separate concerns. The product technology baseline is evidenced by the constitution and repository. Hosting, production deployment, provider quotas and operational SLA remain outside the approved management baseline until the team records them.
