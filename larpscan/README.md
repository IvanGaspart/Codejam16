# LARP-scan front-end

The React and TanStack Start website shows Trottier floor occupancy on a campus map. Open a floor to see its estimated headcount and ten-minute occupancy trend. Live floor readings refresh every 15 seconds. On desktop, **Shift+X** opens X-Ray and shows scanner coverage and Wi-Fi access points, refreshing scanner readings every five seconds.

## Install and run

Install Node.js 22.12 or later and Bun. From the repository root, install the locked dependencies and start the development server.

```sh
cd larpscan
bun install --frozen-lockfile
VITE_API_BASE_URL=http://localhost:8080 bun run dev
```

Open the local URL printed by Vite. Start the [server](../server/README.md) for local readings, or press **Alt+P** to toggle demo data. Omitting `VITE_API_BASE_URL` selects the configured Render API. To use a different API for a production build, set that variable when building.

```sh
bun run test
bun run build
bun run preview
```
