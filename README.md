# TAG

**Your notifications, promoted to Live Updates.**

TAG listens to the notifications your phone already receives and lifts the ones that deserve it into Android 16 Live Updates: a short, glanceable chip in the status bar and a clean card in the shade. A song changes, a call connects, a code arrives, a workout runs, and the chip shows the one thing you need to know.

<!-- Replace with real captures. Suggested: docs/screenshots/dashboard.png, chip.png, inspect.png, stats.png, settings.png -->
<p align="center">
  <img src="docs/screenshots/dashboard.png" width="23%" alt="Dashboard">
  <img src="docs/screenshots/stats.png" width="23%" alt="Interceptions stats">
  <img src="docs/screenshots/settings.png" width="23%" alt="Settings">
  <img src="docs/screenshots/onboarding.png" width="23%" alt="Onboarding">
</p>

## Requirements

- **Android 16 (API 36) or newer.** Live Updates and promoted ongoing notifications don't exist on earlier versions, so TAG has nothing to promote to.
- Notification Listener access and notification permission (both are asked for during onboarding).
- [Shizuku](https://shizuku.rikka.app/) is **optional**. See below.

## What it does

Every tile on the dashboard is a toggle. Lime means on, a chevron means the tile has options of its own.

### Live chips

| Feature | What the chip shows |
| --- | --- |
| **Discord calls** | Voice state: On VC, Muted, Deafened, Connecting |
| **Strava workouts** | Pace, time or distance, your choice, with Pause/Stop actions on the card |
| **ProtonVPN sessions** | The country you're connected to |
| **Weather** | The current temperature, refreshed at an interval you choose |
| **Streaks** | Your current streak from learning and self-improvement apps (will be improved in later versions) |
| **Bluetooth devices** | The device that just connected |
| **Song Up Next** | The song that is playing now |
| **Ambient Music Mod** | The song your phone just recognised |
| **Events** | A countdown to your next calendar event |
| **Sound mode** | Ring, vibrate or silent, as you flip the switch |
| **Downloads / Uploads** | Progress as a percentage |
| **Print in progress** | The job's progress |
| **UPI credit** | Money received, at a glance |
| **OTP / 2FA** | The code itself. Tap the chip to copy it |
| **Pin** | Any short code or string you want kept up in the status bar |
| **Clipboard context** | Useful details about what you just copied (needs Shizuku) |

### Inspect

Select text in any app, choose **Microtag** in the text toolbar, and get a helpful result: definitions, colour swatches, unit and currency conversions, in-line arithmetic, and timespans. There's also an Inspect path for screenshots, using on-device ML Kit text recognition.

Inspect is a side feature rather than a Live Update, so it is always on.

### Interceptions

The counter at the top of the dashboard counts every notification TAG has promoted. Tap it for the stats page: a daily curve, peak hour, best day, streak, a week-at-a-glance grid, time-of-day split, and top sources. One notification is one interception, however many times it updates.

## Shizuku (optional)

TAG works without it. With it, TAG can:

- read your **clipboard in the background** for Clipboard context,
- **stay alive** in the background so the system doesn't kill it,
- be granted the **Read sensitive notifications** permission, which is what lets TAG read OTP codes that other apps hide from listeners.

## Privacy

- Almost everything happens on your device.
- The only network requests are **Wikipedia and dictionary lookups** for Inspect.
- Screenshot Inspect uses **ML Kit on-device**. Images never leave your phone.
- **No analytics. No accounts. No ads.**

### Permissions

| Permission | Why |
| --- | --- |
| Notification Listener | Reads notifications so TAG knows what to promote |
| Post notifications | Shows its chips |
| Promoted notifications | Requests the Live Update treatment on Android 16 |
| Calendar (read) | Event countdowns |
| Nearby devices | Reads the Bluetooth device's name |
| Internet | Inspect lookups |

## Install

Download the latest APK from [**Releases**](../../releases) and install it. TAG is distributed through GitHub Releases only.

On first launch, follow the two onboarding pages: grant the permissions, optionally switch on Shizuku, and step inside.

## Customising

**Settings** has a palette carousel, AMOLED black, appearance (system, light or dark), multicolour tiles, micrographics, reduce motion, haptics, and a replayable intro. Under Battery, TAG can pause itself overnight and while Battery Saver is on. OTP codes still come through.

### Custom rules

Beyond the built-in parsers, TAG reads `assets/custom_rules.json`. Each entry matches a package's notification text with a regex and writes the chip:

```json
{
  "package_name": "com.example.app",
  "regex": "(\\d+)-day streak",
  "pill_text": "$1 d",
  "icon_name": "ic_capsule_bolt",
  "timeout_seconds": 0,
  "tile": "streaks"
}
```

- `pill_text` can use `$1`, `$2`, ... for capture groups. With no `$n` token, the first capture group is used.
- `icon_name` is a drawable name; leave it out for the default bolt.
- `timeout_seconds` of `0` means the chip lives as long as the source notification.
- `tile` ties the rule to a dashboard toggle, so it switches off with it. An unknown tile name drops the rule.

## Building

1. Clone the repo and open it in a recent Android Studio.
2. Let Gradle sync.
3. Run on a device or emulator with Android 16.

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports with a log excerpt are the most useful thing you can send.

## Credits

- [**Shizuku**](https://github.com/RikkaApps/Shizuku) by RikkaApps, for privileged access without root.
- [**Ambient Music Mod**](https://github.com/KieronQuinn/AmbientMusicMod) by Kieron Quinn, whose recognised songs TAG surfaces.
- [**ML Kit**](https://developers.google.com/ml-kit) by Google, for on-device text recognition.
- [**Montserrat**](https://github.com/JulietaUla/Montserrat) by Julieta Ulanovsky and contributors, the app's typeface (SIL Open Font License).
- Micrographic design language inspired by the Nothing OS clock face and community micrographics packs.
- Everyone whose notifications taught the parsers their manners.

TAG is an independent project. Discord, Strava, Proton VPN, Google and other names are trademarks of their owners and are used only to describe compatibility.

## License

[GPL-3.0](LICENSE)
