# LARP-scan Frontend Handoff

**Tasks Covered:**
1. **Issue #35:** Lovable app: building card and floor stack (polling, `NO_DATA`) — Parent: #8 (`RQ-06`), Relates to: #12 (`RQ-10`)
2. **Next Task:** Lovable app: floor view with history chart — Parent: #8 (`RQ-06`), Relates to: #9 (`RQ-07`)

> **How to use this file:** Paste the prompt section below into Lovable alongside your UI/design instructions.

---

## Prompt Context for Lovable

```text
TECHNICAL & API SPECIFICATION (LARP-scan Frontend):
- Stack: React + Vite + TypeScript + Tailwind/shadcn (use Recharts / shadcn charts for the history chart). Do NOT add or configure Supabase; all data comes from an external Spring Boot REST API.
- API Base URL: Read from `import.meta.env.VITE_API_BASE_URL` and default to `"https://codejam16-ui0a.onrender.com"`. Strip any trailing slash before appending `/api/...`. All GET endpoints are public (no API key or auth headers).
- Terminology: Strictly use "Building", "Floor", and "Busyness" in all UI copy and labels. Never use "occupancy", "crowdedness", "load", "library" (for the Building), or "storey/level" (for a Floor).
- Scope: Trottier Building only. Out of scope: seat maps, indoor floorplans, interactive building maps, and busyness predictions.

ENDPOINTS & TYPESCRIPT CONTRACTS:
1. GET /api/buildings
   Returns `Building[]`:
   ```ts
   export interface Building {
     id: string;   // e.g. "trottier"
     name: string; // e.g. "Trottier Building"
   }
   ```
   Example response:
   `[{ "id": "trottier", "name": "Trottier Building" }]`

2. GET /api/buildings/{buildingId}/floors
   Returns `FloorBusyness[]`:
   ```ts
   export type BusynessLevel = "QUIET" | "MODERATE" | "BUSY" | "FULL" | "NO_DATA";

   export interface FloorBusyness {
     floor: number;
     capacity: number;
     covered: boolean;
     estimatedPeople: number;
     busynessPct: number; // integer 0..100
     level: BusynessLevel;
   }
   ```
   Example response:
   `[{ "floor": 1, "capacity": 120, "covered": true, "estimatedPeople": 34, "busynessPct": 28, "level": "QUIET" }]`

3. GET /api/buildings/{buildingId}/floors/{floor}/history?since=<ISO-8601>
   Returns `FloorHistoryPoint[]` (per-minute snapshots):
   ```ts
   export interface FloorHistoryPoint {
     at: string;          // ISO-8601 UTC timestamp, e.g. "2026-11-21T14:03:00Z"
     busynessPct: number; // integer 0..100
     covered: boolean;
   }
   ```
   Example response:
   `[{ "at": "2026-11-21T14:03:00Z", "busynessPct": 28, "covered": true }]`
   - Query param `since`: Pass today's local midnight as an ISO-8601 string (e.g. `new Date(new Date().setHours(0, 0, 0, 0)).toISOString()`) so "today" matches the user's browser timezone rather than UTC.

BEHAVIOR & STATE REQUIREMENTS:
- Navigation Flow (Building card -> Floor stack -> Floor view):
  1. **Building card:** Displays the Building(s) from `GET /api/buildings` (Trottier Building). Clicking it opens/navigates to the Floor stack for that `buildingId`.
  2. **Floor stack:** Lists all Floors vertically, each colored by Busyness level, showing the Busyness percentage or `"No data"`. Clicking any Floor opens the Floor view for that `floor`.
  3. **Floor view:** Includes a back navigation control to return to the Floor stack, and displays:
     - Current Busyness level badge
     - Current smoothed Busyness percentage (or `"No data"` if `level === "NO_DATA"` / `!covered`)
     - Estimated people (`estimatedPeople`, along with seat `capacity` if helpful; hide or show `"—"` when `NO_DATA`)
     - **Today's Busyness history chart** powered by `GET /api/buildings/{buildingId}/floors/{floor}/history`
- Polling:
  - Poll `GET /api/buildings/{buildingId}/floors` every 15 seconds (`15000` ms) while viewing the Floor stack or Floor view.
  - While on the Floor view, poll `GET /api/buildings/{buildingId}/floors/{floor}/history` every 60 seconds (`60000` ms, matching the server's snapshot interval).
  - Clean up all intervals on unmount or route change and avoid overlapping in-flight requests.
- Busyness Levels & `NO_DATA` Handling:
  - `QUIET`: `< 30%`
  - `MODERATE`: `30–59%`
  - `BUSY`: `60–84%`
  - `FULL`: `>= 85%`
  - `NO_DATA`: Whenever `level === "NO_DATA"` OR `covered === false`, render `"No data"` instead of `0%` or any numeric percentage, and style the Floor distinctly as an inactive/uncovered state (never display `0%` or style it as `QUIET`).
  - **History chart `NO_DATA` gaps:** When mapping `FloorHistoryPoint[]` for the chart, map points where `covered === false` to `null` (do not plot `0%` as Quiet) and configure the chart line/area to leave gaps (`connectNulls={false}`). Format `at` timestamps in local browser time on the X-axis and tooltips, and handle empty history arrays cleanly.
- Loading, Cold Starts & Error States:
  - The backend is hosted on a Render free-tier Docker web service (`https://codejam16-ui0a.onrender.com`), which may take 30–50 seconds to wake up on an initial cold start.
  - Show a clear initial loading state while waking/fetching, keep polling resilient to transient errors, and display a clean, non-blocking error message (plus an optional preview/mock fallback toggle if offline or blocked) rather than crashing.
```

---

## Pre-Flight Checklist for Your Render Backend

- **Verify CORS:** Ensure `CorsConfig` in `server/` allows your Lovable preview and published origins (e.g., `https://*.lovable.app` and `https://*.lovableproject.com`) for `GET` requests, otherwise browser `fetch` calls from Lovable will be blocked by CORS.
- **Keep-alive during demos:** Because Render free-tier containers spin down after inactivity, hit `https://codejam16-ui0a.onrender.com/api/buildings` ~1 minute before testing or demoing so the service is warm.