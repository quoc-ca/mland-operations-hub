### 6.2 Source Code Management

Source code is stored in one GitHub repository with the following branches:

| Branch | Purpose |
|---|---|
| `main` | Release baseline; updated from `develop` at each milestone through a pull request |
| `develop` | Integration branch; receives reviewed changes from all members |
| `quoc`, `son`, `khuong`, `thinh`, `dat` | Personal working branches, one per member |
| `feature/...` | Short-lived branches for larger or risky changes |

Each change is merged into `develop` through a pull request and reviewed before merge. Commit messages state the feature and the purpose of the change. Database schema changes are versioned as Flyway migrations, and applied migrations are never modified. Credentials are kept in local `.env` files that are excluded from Git.
