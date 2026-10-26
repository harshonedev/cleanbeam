# 1. Prepare your computer

Clean Beam runs from a computer on the same Wi-Fi as the projector. What you need depends on how
far you go:

| To... | You need |
|---|---|
| Debloat, restore, push wallpapers | `adb` and `git` |
| Build and install the launcher or the remote receiver | + **JDK 17** (Android Studio optional) |
| Build the phone app | + **Flutter** 3.41+ |
| Talk to the remote receiver from the command line | + **Node.js** 18+ (optional, `tools/wsctl.mjs`) |

The scripts in `tools/` are bash. They run as-is on macOS and Linux, and on Windows in **Git Bash**
(which comes with Git for Windows).

---

## macOS

```sh
# Homebrew (skip if you have it): https://brew.sh
brew install --cask android-platform-tools     # adb
brew install git
brew install --cask temurin@17                 # JDK 17, only to build the apps
brew install --cask flutter                    # only to build the phone app
```

Check: `adb version` and `java -version` (should say 17).

**macOS Local Network permission.** On macOS 15 and later, a terminal needs permission to reach
devices on your network. If `adb connect` says `No route to host` while the projector is clearly
on, open System Settings → Privacy & Security → **Local Network**, turn on your terminal app (Terminal,
iTerm, VS Code...), then run `adb kill-server` and try again.

## Linux

Debian / Ubuntu / Mint:

```sh
sudo apt update
sudo apt install adb git openjdk-17-jdk
```

Fedora: `sudo dnf install android-tools git java-17-openjdk-devel`.
Arch: `sudo pacman -S android-tools git jdk17-openjdk`.

Flutter (only for the phone app): `sudo snap install flutter --classic`, or follow
[flutter.dev/docs/get-started/install/linux](https://docs.flutter.dev/get-started/install/linux).

The `adb` in some distro repos is old. If `adb connect` behaves strangely, use Google's
platform-tools instead:

```sh
curl -LO https://dl.google.com/android/repository/platform-tools-latest-linux.zip
unzip platform-tools-latest-linux.zip -d ~/
echo 'export PATH="$HOME/platform-tools:$PATH"' >> ~/.bashrc && source ~/.bashrc
```

## Windows

1. **Git for Windows**: [git-scm.com/download/win](https://git-scm.com/download/win). Keep the default
   options. This also installs **Git Bash**, where you'll run Clean Beam's scripts.
2. **adb**: download [platform-tools for Windows](https://dl.google.com/android/repository/platform-tools-latest-windows.zip)
   and unzip it to `C:\platform-tools`. Then add that folder to your PATH: Start → "Edit the system
   environment variables" → Environment Variables → `Path` → New → `C:\platform-tools`.
   Or, from PowerShell: `winget install Google.PlatformTools`.
3. **JDK 17** (to build the apps): `winget install EclipseAdoptium.Temurin.17.JDK`.
4. **Flutter** (phone app only): [docs.flutter.dev/get-started/install/windows](https://docs.flutter.dev/get-started/install/windows).

Open **Git Bash** (not cmd or PowerShell) and check `adb version`. Every command in these guides works
there, `tools/*.sh` included.

Windows notes:
- Gradle on Windows is `gradlew.bat`, or `./gradlew` from Git Bash.
- If Windows Firewall asks about `adb.exe`, allow it on **private** networks.
- If you edit a debloat list in Notepad, that's fine: `tools/debloat.sh` strips the BOM and CRLF
  line endings Notepad adds.

---

## Get Clean Beam

```sh
git clone https://github.com/harshonedev/cleanbeam
cd cleanbeam
```

Run every command in the guides from this folder.

## Prebuilt APKs or build yourself

If the repo's **Releases** page has APKs, download `cleanbeam-launcher-full.apk` (or `-lite`),
`cleanbeam-remote-receiver.apk` and `cleanbeam-remote-phone.apk`. You then only need `adb`.

Otherwise build them. The guides show how; each one is a single Gradle command once JDK 17 is
installed. The first build downloads Gradle and the Android SDK pieces it needs, so it takes a
while.

If Gradle can't find the Android SDK, install [Android Studio](https://developer.android.com/studio)
once (it installs the SDK), or create `launcher/local.properties` with:

```
sdk.dir=/path/to/Android/sdk
```

The usual SDK paths are `~/Library/Android/sdk` on macOS, `~/Android/Sdk` on Linux and
`C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk` on Windows, with the backslashes doubled.

Next: [2. Connect to your projector](02-connect-your-projector.md)
