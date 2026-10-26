# Contributing to Clean Beam

The most useful thing you can send is **your projector**: a tested debloat list and what you learned
about its firmware. Code, themes, translations and docs are welcome too.

## Add a projector

1. Follow the [debloat guide](docs/guides/03-debloat.md) on your projector, or paste the "Add my
   projector" prompt from [PROMPTS.md](PROMPTS.md) into an AI assistant.
2. Create `models/<brand>-<model>/` (lowercase, dashes: `magcubic-hy300-pro`) with three files.
   Copy [`models/egate-atom-3x/`](models/egate-atom-3x/) as a template.

   | File | What goes in it |
   |---|---|
   | `debloat.txt` | One package per line, grouped, with a comment for each group. A header with the board, Android version and adb port, plus a **"Kept on purpose"** list (projector controls, HDMI input layer, vendor settings) so the next person doesn't disable them. |
   | `device.json` | Name, brand, `"kind": "projector"` (or `"tv"` / `"box"`), Android version, platform, adb port, any vendor Home property, what you tested. |
   | `README.md` | How Home is chosen on this firmware, the screensaver, where focus and keystone live, quirks. |

3. Add a row to **Supported projectors** in `README.md`.
4. Open a pull request saying what you tested: boot, HDMI, focus, keystone, Wi-Fi, Bluetooth,
   remote, sound, launcher, phone remote.

Rules for lists:
- **Only list what you tested on real hardware.** Mark guesses with `# untested:` at the start of the
  line (commented out), so nobody runs them by accident.
- Never list core Android, Play, keyboards, the TV input layer or the current Home app.
  `tools/debloat.sh` refuses most of these anyway.
- No personal data: no IPs, Wi-Fi names, serial numbers, tokens. Blur them in screenshots.
- Same firmware family as an existing model? Say so in the header and link it.

Lists from [cleanyourtv](https://github.com/Gathin23/cleanyourtv/tree/main/examples) are MIT
licensed. If you start from one, keep a line in the header:
`# Based on cleanyourtv examples/<folder> (MIT, (c) Gathin and contributors)`.

## Code

| Part | Stack | Build and test |
|---|---|---|
| `launcher/` | Kotlin, Android Views, minSdk 28 | `./gradlew :app:assembleFullDebug :app:assembleLiteDebug :app:testFullDebugUnitTest :app:testLiteDebugUnitTest` |
| `remote/receiver/` | Kotlin, minSdk 28 | `./gradlew assembleDebug test` |
| `remote/phone/` | Flutter 3.41+ | `flutter test && flutter analyze`, `cd android && ./gradlew assembleDebug` |
| `tools/` | bash (runs in Git Bash on Windows) | Run against a device, with `DRY_RUN=1` where supported |

- Keep the launcher light: it runs on 1 GB boxes with Mali-450-class GPUs. No per-frame work on
  static themes, and no new dependencies without a reason.
- Lite must keep working with **no `INTERNET` permission**. Put catalogue code under `src/full/`.
- Projector-specific behaviour (vendor intents, Home properties) is detected at runtime, not
  hardcoded per brand.
- Match the style around you. Commit messages: `feat(launcher): ...`, `fix(remote): ...`,
  `docs: ...`, `models: add wanbo-x5-pro`.

## Screenshots

Take them from the device (`adb exec-out screencap -p > shot.png`), crop nothing important, blur
Wi-Fi names, and keep them 1280×720 JPGs under 300 KB in `docs/images/`.

## Code of conduct

Be kind and assume good faith. People here are fixing their own projectors in their spare time.
