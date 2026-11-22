# LARP-scan · Code Jam 16

LARP-scan helps students find a quieter floor in McGill’s Trottier Building. Scanner laptops measure the signal strength of nearby Bluetooth devices and use Wi-Fi access points to locate their readings. The server combines overlapping readings into a headcount estimate and compares it with each floor’s capacity. The website shows current occupancy, recent trends, and where scanners have coverage. Floors without recent coverage show **No data**.

## Subsystems

| Subsystem | Role |
| --- | --- |
| [Service](service/README.md) | Runs on scanner laptops and sends a batch of Bluetooth and Wi-Fi readings every 15 seconds. |
| [Server](server/README.md) | Stores batches in PostgreSQL, estimates occupancy, and serves the public read API. |
| [Front-end](larpscan/README.md) | Displays the building map, floor occupancy, recent trends, and X-Ray scanner overlay. |

## Local setup

From a clone of this repository, start the server and its database first. In separate terminals, run the service on a Wi-Fi and Bluetooth-equipped laptop and start the front-end. Each subsystem README above includes its prerequisites and commands. To explore the interface without scanner hardware, use the front-end’s demo mode.

[System specification](docs/SPEC.md) · [What a scanner batch contains](docs/adr/0002-rssi-only-batches.md)

Built by Zach Chuang, Jacob Sauvé, and Ivan Gaspart.
