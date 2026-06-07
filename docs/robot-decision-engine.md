# Robot Decision Engine

Robots use a lightweight Utility AI loop:

1. `RobotActivityService` samples active robots.
2. `RobotBrainService` builds a `RobotDecisionContext`.
3. `RobotDecisionAction` implementations score candidate actions.
4. The highest scoring action executes through `RobotActionSupport` or a domain service.

When adding any new gameplay feature, check whether robots need to perceive or use it:

- Add context fields or sensors when the feature changes robot decisions.
- Add a `RobotDecisionAction` when robots should actively use the feature.
- Add decision reasons to activity logs so behavior can be inspected from the backend UI.
- Keep actions small: scoring decides what to do, support/domain services execute how to do it.

Current robot action families:

- Dungeon progression and loot handling.
- Planned equipment enhancement.
- Market buying and market supply.
- Social chat based on short-term goals.
- Rest fallback when no useful action is available.

Database note: this single-player learning project intentionally keeps one
mutable Flyway migration, `V1__latest_schema.sql`. If the local schema history
does not match the latest file, startup cleans and rebuilds the database.
