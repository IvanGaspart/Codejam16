>Those who seek, shall find.

Jameson MacGill, you sly fox. **Forty thousand** McGill students left with a measly **three thousand** library spots. The search for the study abode devours precious time, especially during the final days of the semester. And time is something McGillians do not have. We present to you the solution to our headaches, the product asked and made by the community: [**LARPSCAN**](https://larpscan.lovable.app). Fear not, for every nook and cranny of every library will be of no secret to you. McGill has left us with no choice other than to optimize the heck out of library capacity usage and that is what we shall do. 

# LARP-scan · Code Jam 16

LARP-scan (Library Address Resolution Protocol) is a tool to help McGill students find a free spot in the library. Scanner laptops measure the signal strength of nearby Bluetooth devices and use Wi-Fi access points to locate their readings. The server combines overlapping readings into a headcount estimate and compares it with each floor’s capacity. The website shows client devices current occupancy, recent trends, and where scanners have coverage. Floors without recent coverage show **No data**.
<br>
<br>
<img width="2940" height="1520" alt="image" src="https://github.com/user-attachments/assets/ba8a3154-7e6a-4907-975a-e9c4b5a01be5" />


## Motivation

### It’s so useful!
At McGill during exam season, it is always a gamble whether or not you will end up finding a spot in the library or simply have a stress-inducing, chronophagous stroll.
LARP-scan saves you from unfortunate library larping by allowing you to gauge study spot viability **on the go**, from your laptop or phone!

### We were so close!

**Collective amnesia** has hit the McGill community. [Waitz](https://waitz.io/index.html) was the old system at McGill to track library busyness, from 2020 to 2022. However, they required IoT devices to be placed in each and every room they wanted to track. The McLennan construction stopped the initiative but with little reading between the lines skill, it can be (quite easily) understood money and the libraries were no longer a pair (decision of McGill corporate). Larp-scan removes the need for these devices, gathering data from voluntary contributors running the larp-scan service.

## Subsystems

| Subsystem | Role |
| --- | --- |
| [Service](service/README.md) | Runs on scanner laptops and sends a batch of Bluetooth and Wi-Fi readings every 15 seconds. |
| [Server](server/README.md) | Stores batches in PostgreSQL, estimates occupancy, and serves the public read API. |
| [Front-end](larpscan/README.md) | Displays the building map, floor occupancy, recent trends, and X-Ray scanner overlay. |

```mermaid
flowchart LR
    Scanner["Scanner service"] -->|"Bluetooth and Wi-Fi readings"| Server["Occupancy server"]
    Server <-->|"Batches and floor snapshots"| Database[("PostgreSQL")]
    Frontend["Front-end"] <--|"Requests occupancy, history, and X-Ray data"| Server
```

## Local setup

From a clone of this repository, start the server and its database first. In separate terminals, run the service on a Wi-Fi and Bluetooth-equipped laptop and start the front-end. Each subsystem README above includes its prerequisites and commands. To explore the interface without scanner hardware, use the front-end’s demo mode.

[System specification](docs/SPEC.md) · [What a scanner batch contains](docs/adr/0002-rssi-only-batches.md)

Built by Zach Chuang, Jacob Sauvé, and Ivan Gaspart.

