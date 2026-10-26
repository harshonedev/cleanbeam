# 2. Connect to your projector

Clean Beam talks to the projector over **ADB** (Android Debug Bridge) on your Wi-Fi. No cable and no
root are needed.

## Turn on developer options

On the projector:

1. **Settings → Device Preferences → About** (on some firmware it's **Settings → About** or
   **System → About**).
2. Scroll to **Build** (or **Build number**) and press OK on it **7 times**. You'll see "You are now
   a developer".
3. Go back. **Developer options** now appears (under Device Preferences or System).
4. Turn on **USB debugging**. If you see **Network debugging**, **ADB over network** or
   **Wireless debugging**, turn that on too.

Some projectors hide the stock Settings app behind their own. If you can't find "About", open the
projector's own settings, look for "More settings", "Android settings" or "Advanced", and go from
there.

## Find the projector's IP address

Use any of these:
- On the projector: **Settings → Network & Internet → (your Wi-Fi) → IP address**.
- On your router's page, in the list of connected devices.
- From your computer: `adb mdns services` lists ADB devices that announce themselves.

## Connect

```sh
adb connect 192.168.1.50:5555
```

If that's refused, try **3333**. Many projector boards (Amlogic, Allwinner) use it:

```sh
adb connect 192.168.1.50:3333
```

The first time, the projector may ask **"Allow USB debugging?"**. Tick "Always allow from this
computer" and press OK.

Check with `adb devices`. You want to see `192.168.1.50:5555  device`. `unauthorized` means you need
to accept the prompt on the projector, and `offline` means you should run `adb disconnect` and connect
again.

### Neither port works

- **Wireless debugging** (Android 11+, more common on Google TV): Developer options → Wireless
  debugging → **Pair device with pairing code**. Then on the computer:
  ```sh
  adb pair 192.168.1.50:<pairing-port>     # enter the 6-digit code it shows
  adb connect 192.168.1.50:<port shown under "IP address & Port">
  ```
- **Another port**: some firmware uses a fixed odd port (5877 has been seen). If `nmap` is installed,
  `nmap -p 1-65535 --open 192.168.1.50` lists the open ports. Try `adb connect` on each.
- **Wi-Fi client isolation**: some routers, guest networks and mesh systems stop Wi-Fi devices from
  talking to each other. If the projector answers nothing at all (not even "refused"), put the
  computer on the same network, not the guest one, or plug it into the router with a cable.
- **USB**: projectors with a USB-A port can't be reached with a normal cable. Network ADB is the way.

### Several devices connected

Once more than one device shows in `adb devices` (a phone over USB, say), tell `adb` and the
scripts which one you mean:

```sh
export ANDROID_SERIAL=192.168.1.50:5555      # macOS, Linux, Git Bash
```

## The IP keeps changing

Most projectors get a new IP from the router after a reboot. Either reserve an address for it in
your router (look for "DHCP reservation" or "static lease"), or find it again with
`adb mdns services`.

## Security

Many projectors run network ADB with **no authorisation at all** (`ro.adb.secure=0`). The prompt
never appears, and *anyone on your Wi-Fi* can get a shell on the projector. To check:

```sh
adb shell getprop ro.adb.secure      # 0 or empty = open
```

The phone remote relies on exactly this to inject key presses, so leave network ADB on only if you
trust your Wi-Fi. If you don't need the remote, switch **USB debugging** and **Network debugging**
off when you're done (Developer options) and reboot.

Next: [3. Debloat](03-debloat.md)
