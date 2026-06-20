# Robot Decision Engine

Robots use a lightweight Utility AI loop:

1. `RobotActivityService` samples active robots. How many act this tick is scaled by
   time of day (`activityFactor`) so the population has evening peaks and overnight
   lulls instead of flat 24/7 activity.
2. `RobotBrainService` builds a `RobotDecisionContext` (now also carrying the robot's
   recent action history and guild membership).
3. `RobotDecisionAction` implementations score candidate actions.
4. An action is chosen by **weighted-random (softmax) sampling**, not argmax: the
   highest-utility action is most likely but lower-utility ones keep a non-zero
   chance, so robots "satisfice" like people instead of always grabbing the optimum
   (which starved low-value actions and looked robotic). The chosen action executes
   through `RobotActionSupport` or a domain service.

Key levers for human-likeness:

- **Selection temperature** (`RobotBrainService.temperature`) is the human-vs-expert
  dial. Its base comes from the robot's `RobotArchetype`; it rises at night.
- **`RobotArchetype`** (resolved from the `personality_archetype` column) gives each
  robot structured behaviour weights (pve/market/social/growth bias) that multiply
  the per-action personality bonus — replacing the old "keyword substring -> fixed
  bonus" path so the 200 robots actually diverge.
- **`RobotMemoryService`** keeps a short per-robot ring buffer of recent action kinds
  and chat lines. Actions apply a *decaying* `repeatPenalty` (replacing the old
  one-step `isCurrentKind` check) to stop A→B→A→B flip-flopping, and chat de-dupes
  against it.
- **Response curves** (`RobotResponseCurves`) shape considerations non-linearly
  (e.g. stamina urgency is logistic, power-gap is quadratic) instead of linear clamps.

Note: `RobotDecisionAction.priority()` only orders the action list and is **not** part
of scoring — changing it does not change behaviour. Tune `score()` and temperature.

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
mutable latest schema file, `backend-ddd/mythic-realm-starter/src/main/resources/db/latest_schema.sql`.
Flyway is not used; if the recorded local checksum does not match the latest file,
startup cleans and rebuilds the database.
