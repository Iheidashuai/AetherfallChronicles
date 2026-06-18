# UI Design: Desktop Web H5 Game

Feature: 001-h5-java-backend-migration
Status: Active guidance
Created: 2026-06-06

## Design Direction

The H5 game is a responsive desktop-first web game. It is not a phone emulator and should not force the player through a single vertical column on normal desktop screens.

Primary experience:

- Horizontal workbench layout with multiple panels visible at once.
- Dense but readable RPG operations UI.
- Internal scrolling inside lists and logs.
- Persistent action areas for high-frequency actions.
- Narrow viewport fallback remains usable, but desktop is the primary design target.

## Layout Rules

- The outer shell may constrain width for readability, but should expand to desktop sizes around 1200-1480px.
- Avoid one long page for complex views. Split into fixed or flexible columns.
- Use internal scroll containers for inventory item grids, dungeon grids, quest lists, market lists, battle logs, leaderboard, and chat history.
- Keep primary controls visible without requiring page-bottom scrolling.
- Do not put the whole app in a fixed mobile phone frame on desktop.

## Screen Guidelines

### Home

- Header shows character identity and key resources.
- Main workspace splits into equipment summary and navigation/actions.
- Navigation should be a grid of game modules, not a long vertical menu.

### Inventory

- Desktop layout is three columns:
  - Left: backpack capacity, combat power, gold, equipped items.
  - Center: item grid for current category.
  - Right: filters, sort controls, bulk sell controls.
- Supported category filters: all, weapon, armor, accessory.
- Supported organize modes: quality, level, type.
- Bulk sell must support explicit quality choices:
  - common
  - uncommon
  - rare
  - epic
  - legendary
- If a category filter is active, bulk sell applies to that category only.
- Item cards show type, level, quality, display name, and key stats.
- Item detail modal shows full stat breakdown and sell price.

### Dungeon

- Dungeon list is a multi-column grid on desktop.
- Each dungeon card shows difficulty, level, recommended power, and risk label.
- Risk labels should make combat-power mismatch obvious before the player starts.

### Battle Result

- Desktop layout is two columns:
  - Left: summary, speed controls, loot cards.
  - Right: battle log/stage.
- Battle logs play progressively and support 1x, 2x, 4x, and skip/all.
- Loot must be presented as clickable equipment cards with quality color and detail modal.
- Failed runs should be visually distinct and should not look like full success.

### Chat

- Chat workspace must fit in the viewport.
- Header/channel strip stays visible.
- Message list scrolls internally.
- On entering chat or receiving refreshed data, scroll to the newest message.
- Input stays visible at the bottom.
- History is reached by scrolling upward.

### Leaderboard

- Show summary metrics above list: robot/player count, current player rank, top power.
- List scrolls internally.
- Current player row must be visually distinct.
- Robot population should feel active; target at least 200 robots for the current milestone.

## Visual System

- Dark metallic fantasy operations surface.
- Cards should use 8px radius or less.
- Quality colors:
  - common: neutral gray
  - uncommon: green
  - rare: blue
  - epic: violet
  - legendary: amber
- Use concise action buttons and icon support where available.
- Avoid oversized mobile hero typography inside operational panels.
- No decorative blobs/orbs or marketing hero treatment.

## Current Implementation References

- Frontend app: `web/src/App.tsx`
- Frontend styles: `web/src/styles.css`
- Backend battle authority: `backend-ddd/mythic-realm-api/src/main/java/com/mythicrealm/api/gameplay/dungeon/DungeonService.java`
- Backend inventory authority: `backend-ddd/mythic-realm-api/src/main/java/com/mythicrealm/api/gameplay/inventory/InventoryService.java`
- Robot population and latest schema: `backend-ddd/mythic-realm-starter/src/main/resources/db/latest_schema.sql`
