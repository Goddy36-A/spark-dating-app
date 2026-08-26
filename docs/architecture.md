# Architecture

## Overview

Spark follows Clean Architecture with three layers — UI, Domain, and Data — enforced via the Android multi-module structure. Feature modules depend on core modules; core modules never depend on feature modules.

```
┌──────────────────────────────────────────────────────┐
│                    app module                        │
│  (navigation, DI wiring, MainActivity, Application)  │
└────────────────────────┬─────────────────────────────┘
                         │ depends on
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
    feature:auth   feature:discovery  feature:chat  …
          │              │              │
          └──────┬────────┘──────────────┘
                 ▼
         core:ui  core:auth  core:network  core:database  core:model
```

## Data flow

```
Composable
  └─ observes StateFlow
       └─ ViewModel (Hilt @HiltViewModel)
            └─ Repository (injected interface)
                 └─ Supabase SDK (PostgREST / Realtime / Storage)
                      └─ PostgreSQL (Supabase managed)
```

## Module dependency graph

```mermaid
graph TD
    app --> feature_auth
    app --> feature_onboarding
    app --> feature_discovery
    app --> feature_matching
    app --> feature_chat
    app --> feature_profile
    app --> feature_settings
    app --> feature_subscription
    app --> feature_safety
    app --> core_ui
    app --> core_auth
    app --> core_network
    app --> core_database

    feature_auth --> core_auth
    feature_auth --> core_ui
    feature_auth --> core_model

    feature_discovery --> core_network
    feature_discovery --> core_auth
    feature_discovery --> core_ui
    feature_discovery --> core_model

    feature_chat --> core_network
    feature_chat --> core_auth
    feature_chat --> core_ui
    feature_chat --> core_model

    core_auth --> core_network
    core_auth --> core_model
    core_network --> core_model
    core_database --> core_model
```

## Authentication flow

```mermaid
sequenceDiagram
    participant App
    participant AuthVM
    participant AuthRepo
    participant Supabase

    App->>AuthVM: login(email, password)
    AuthVM->>AuthVM: validate inputs
    AuthVM->>AuthRepo: login(email, password)
    AuthRepo->>Supabase: auth.signInWith(Email)
    Supabase-->>AuthRepo: Session + JWT
    AuthRepo->>Supabase: postgrest["users"].select(id)
    Supabase-->>AuthRepo: User record
    AuthRepo-->>AuthVM: User
    AuthVM->>App: AuthState.Authenticated
    App->>App: Navigate to Main
```

## Match creation flow

```mermaid
sequenceDiagram
    participant UserA as User A (Android)
    participant DB as Supabase DB
    participant Trigger as DB Trigger
    participant FCM as FCM

    UserA->>DB: INSERT likes (liker=A, liked=B)
    DB->>Trigger: on_like_inserted fires
    Trigger->>DB: SELECT likes WHERE liker=B AND liked=A
    alt Mutual like exists
        Trigger->>DB: INSERT matches (user1=A, user2=B)
        Trigger->>DB: INSERT conversations
        Trigger->>DB: INSERT conversation_members (A, B)
        Trigger->>DB: INSERT notification_queue (A), (B)
        DB->>FCM: send-notification Edge Function
        FCM-->>UserA: "It's a match!" push
    else No mutual like
        Trigger->>Trigger: no-op
    end
```

## Real-time messaging

```mermaid
sequenceDiagram
    participant A as User A
    participant Realtime as Supabase Realtime
    participant DB as PostgreSQL
    participant B as User B

    A->>DB: INSERT messages (content, conversation_id)
    DB->>Realtime: WAL event → postgres_changes
    Realtime-->>A: INSERT event (own message confirmed)
    Realtime-->>B: INSERT event (new message received)
    B->>DB: UPDATE conversation_members SET last_read_at
```

## Security model

- **Row Level Security** enforces that users can only read/write their own data
- **Service Role** is only used in the admin dashboard — never in the Android app
- **Anon key** is safe to embed in the Android app (RLS enforces all restrictions)
- **Age validation** happens in the `discover_profiles` DB function — the client cannot bypass it
- **Proximity** is calculated server-side; exact coordinates never leave the database
