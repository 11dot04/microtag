# Contributing to TAG

Thanks for wanting to help. TAG is a small, opinionated project, so a quick note before you start.

## Reporting a bug

Open an issue using the **Bug report** template. The most useful thing you can add is the matching entry from the in-app **Logs** screen (open it from the dashboard). It records which rule or parser looked at a notification and what it decided.

Please include:
- Your device, Android version and TAG version (Settings, About)
- Whether Shizuku is on
- The app whose notification misbehaved, and what you expected the chip to say

Don't paste anything private. Logs can contain notification text, so redact names, numbers and codes.

## Asking for an app or a feature

Open a **Feature request**. For a new app, the notification itself is what matters: a Logs entry of the real notification (redacted) usually lets a rule be written in minutes.

## Adding a rule

Many apps need no code at all. Add an entry to `assets/custom_rules.json`:

- `package_name`, the app's package
- `regex`, matched against the notification title and text joined by a space
- `pill_text`, with `$1`, `$2`, ... for capture groups
- `icon_name`, optional drawable name
- `timeout_seconds`, `0` to follow the source notification
- `tile`, the dashboard toggle that owns the rule

Test it against real notifications, and keep the chip short: status-bar pills have room for a handful of characters.

## Pull requests

1. Open an issue first for anything bigger than a fix, so we agree on the direction.
2. Keep each PR to one change.
3. Describe what you tested and on which Android version. TAG needs Android 16 (API 36).
4. Follow the existing code style and naming.

### Kotlin naming

- Prefix private backing state with an underscore (for example `_microOn`).
- Never declare two members whose names differ only by the case of their first letter. `microOn` and `MicroOn` both compile to `getMicroOn()` and fail with a platform declaration clash.

### Design

TAG has a deliberate look: straight-cut brutalist shapes, a palette system, and motion with springs. If you add UI, match the existing language and respect the Reduce motion and Haptics settings.

## Licensing

By contributing you agree that your work is released under the project's [GPL-3.0](LICENSE) license.
