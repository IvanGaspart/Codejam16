# LARP-scan

LARP-scan (Library Address Resolution Protocol) estimates how busy McGill library spaces are by listening for nearby Bluetooth Low Energy devices from students' laptops, so students can check before walking over.

## Language

**LARP-scan**:
The whole system: Scanners, server, and website.
_Avoid_: the app, Waitz clone

### Places

**Building**:
A McGill library building whose Busyness LARP-scan reports. Trottier is the only Building for now.
_Avoid_: library, site

**Floor**:
One level of a Building, with a seat capacity.
_Avoid_: level, storey, zone

**Access Point (AP)**:
A Wi-Fi access point, identified by its BSSID, known to sit on exactly one Floor.
_Avoid_: router, hotspot

### Sensing

**Scanner**:
One running instance of the LARP-scan service on a laptop, which listens for BLE devices and reports what it hears.
_Avoid_: sniffer, agent, client

**Sighting**:
One BLE address heard by one Scanner at one moment, with its signal strength and the AP the Scanner was connected to.
_Avoid_: ping, packet, scan result

### Results

**Busyness**:
The estimated share of a Floor's capacity in use, shown as a level (Quiet / Moderate / Busy / Full) and a percentage.
_Avoid_: occupancy, crowdedness, load
