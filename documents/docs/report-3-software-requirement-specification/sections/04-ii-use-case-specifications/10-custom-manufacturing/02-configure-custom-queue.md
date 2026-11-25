### 10.2 Configure Custom Queue

> **Not implemented; TBD — Mland decision required (checked 2026-11-25):** the current Feature-004 specification and the code contain no custom-queue configuration. The description below records the earlier requirement only and defines no confirmed flow.

#### Primary Actors

- Manager
- Admin

#### Secondary Actors

- None

#### Description

This use case (use case 68) lets a Manager or Admin configure the minimum custom-order deadline, the dates that cannot be selected as a deadline and the maximum custom-order queue size.

#### Preconditions

1. The actor is signed in as Manager or Admin.

#### Business Rules

- BR-68-01

#### Business Rule Definitions

| ID | Rule Definition |
|---|---|
| BR-68-01 | Only a Manager or Admin may change custom-queue values; their permitted ranges and effects are TBD. |
