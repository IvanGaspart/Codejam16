// WifiInfo: tiny agent app that reads the current Wi-Fi BSSID/SSID via CoreWLAN.
//
// It must run as a real .app bundle (launched through `open`), because macOS
// grants Location Services per app. Result is written as JSON to --out <path>
// (or stdout if omitted):
//   {"bssid": "aa:bb:cc:dd:ee:ff", "ssid": "MyNet"}   or   {"error": "denied"}

import Cocoa
import CoreLocation
import CoreWLAN

func arg(_ name: String) -> String? {
    let a = CommandLine.arguments
    guard let i = a.firstIndex(of: name), i + 1 < a.count else { return nil }
    return a[i + 1]
}

let outPath = arg("--out")

func finish(_ payload: [String: Any]) -> Never {
    let data = (try? JSONSerialization.data(withJSONObject: payload)) ?? Data("{\"error\":\"encode\"}".utf8)
    if let p = outPath {
        try? data.write(to: URL(fileURLWithPath: p))
    } else {
        FileHandle.standardOutput.write(data + Data("\n".utf8))
    }
    exit(0)
}

final class AppDelegate: NSObject, NSApplicationDelegate, CLLocationManagerDelegate {
    let manager = CLLocationManager()
    var asked = false

    func applicationDidFinishLaunching(_ notification: Notification) {
        // Give the user time to answer the one-time permission prompt.
        DispatchQueue.main.asyncAfter(deadline: .now() + 60) { finish(["error": "timeout"]) }
        NSApp.activate(ignoringOtherApps: true)
        manager.delegate = self
        handle(manager.authorizationStatus)
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        handle(manager.authorizationStatus)
    }

    func handle(_ status: CLAuthorizationStatus) {
        switch status {
        case .notDetermined:
            if !asked {
                asked = true
                manager.requestWhenInUseAuthorization()
            }
        case .denied, .restricted:
            finish(["error": "denied"])
        default:  // authorized (always / when in use)
            readWiFi()
        }
    }

    func readWiFi() {
        let iface = CWWiFiClient.shared().interface()
        let bssid = iface?.bssid()
        let ssid = iface?.ssid()
        if bssid == nil && ssid == nil {
            finish(["error": "unavailable"])  // not connected, or still gated
        }
        finish([
            "bssid": bssid?.lowercased() ?? NSNull(),
            "ssid": ssid ?? NSNull(),
        ])
    }
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)  // no Dock icon
let delegate = AppDelegate()
app.delegate = delegate
app.run()
