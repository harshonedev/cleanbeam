import 'package:flutter/material.dart';

import '../protocol/message.dart' as msg;
import '../transport/command_sink.dart';
import '../transport/transport.dart';
import 'keyboard_sheet.dart';
import 'theme.dart';
import 'widgets/dpad.dart';
import 'widgets/hold_button.dart';
import 'widgets/shortcuts_row.dart';

class RemoteScreen extends StatelessWidget {
  final CommandSink sink;

  /// Projector/system screens the receiver can open; empty hides the row.
  final List<msg.ShortcutItem> shortcuts;

  /// Package → label from the receiver's app list, used for the "On …" status line.
  final Map<String, String> appLabels;
  const RemoteScreen({super.key, required this.sink, this.shortcuts = const [], this.appLabels = const {}});

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.sizeOf(context).width;
    final dpad = (width - 64).clamp(220.0, 320.0);
    return ListenableBuilder(
      listenable: sink,
      builder: (context, _) => SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        child: Column(
          children: [
            _StatusLine(sink: sink, appLabels: appLabels),
            const SizedBox(height: 20),
            DPad(send: sink.send, diameter: dpad),
            const SizedBox(height: 24),
            _row([
              _pill('btn-back', const Icon(Icons.arrow_back_rounded), () => sink.send(msg.key(4))),
              _pill('btn-home', const Icon(Icons.home_rounded), () => sink.send(msg.key(3))),
              _pill('btn-menu', const Icon(Icons.menu_rounded), () => sink.send(msg.key(82))),
            ]),
            const SizedBox(height: 14),
            _row([
              _hold('btn-vol-down', const Icon(Icons.remove_rounded), 25),
              _pill('btn-mute', const Icon(Icons.volume_off_rounded), () => sink.send(msg.key(164))),
              _hold('btn-vol-up', const Icon(Icons.add_rounded), 24),
            ]),
            const SizedBox(height: 14),
            _row([
              _pill('btn-rew', const Icon(Icons.fast_rewind_rounded), () => sink.send(msg.key(89))),
              _pill('btn-play', const Icon(Icons.play_arrow_rounded), () => sink.send(msg.key(85))),
              _pill('btn-ffwd', const Icon(Icons.fast_forward_rounded), () => sink.send(msg.key(90))),
            ]),
            const SizedBox(height: 24),
            _row([
              _pill(
                'btn-keyboard',
                const Row(mainAxisSize: MainAxisSize.min, children: [Icon(Icons.keyboard_rounded), SizedBox(width: 8), Text('Type')]),
                () => showKeyboardSheet(context, sink),
              ),
              _pill(
                'btn-power-menu',
                const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [Icon(Icons.power_settings_new_rounded), SizedBox(width: 8), Text('Power menu')],
                ),
                () => sink.send(msg.key(135)),
                color: Tokens.surface,
              ),
            ]),
            if (shortcuts.isNotEmpty) ...[
              const SizedBox(height: 28),
              ShortcutsRow(items: shortcuts, onOpen: (id) => sink.send(msg.open(id))),
            ],
          ],
        ),
      ),
    );
  }

  Widget _row(List<Widget> children) => Row(
    mainAxisAlignment: MainAxisAlignment.center,
    children: [
      for (var i = 0; i < children.length; i++) ...[if (i > 0) const SizedBox(width: 12), children[i]],
    ],
  );

  Widget _pill(String k, Widget child, VoidCallback onTap, {Color? color}) =>
      HoldButton(key: Key(k), onTap: onTap, color: color, child: child);

  Widget _hold(String k, Widget child, int code) => HoldButton(
    key: Key(k),
    mode: HoldMode.downUp,
    onTap: () => sink.send(msg.key(code)),
    onDown: () => sink.send(msg.key(code, action: 'down')),
    onUp: () => sink.send(msg.key(code, action: 'up')),
    child: child,
  );
}

class _StatusLine extends StatelessWidget {
  final CommandSink sink;
  final Map<String, String> appLabels;
  const _StatusLine({required this.sink, this.appLabels = const {}});

  @override
  Widget build(BuildContext context) {
    final s = sink.lastStatus;
    final kind = sink.kind;
    final String line;
    if (kind == null) {
      line = sink.connecting ? 'Looking for the projector' : (sink.lastError ?? 'Not connected');
    } else if (s == null) {
      line = 'Connected';
    } else {
      final app = s.app == null ? 'Home' : friendlyAppName(s.app!, appLabels);
      line = s.screenOn ? 'On $app' : 'Screen off';
    }
    return Row(
      children: [
        Expanded(
          child: Text(
            line,
            style: const TextStyle(color: Tokens.muted, fontSize: 15),
            overflow: TextOverflow.ellipsis,
          ),
        ),
        TransportBadge(kind: kind, connecting: sink.connecting),
      ],
    );
  }
}

/// System and projector packages first (their launcher labels are misleading, e.g. the vendor
/// settings app lists itself as "More Apps"), then the receiver's app labels, then the package tail.
String friendlyAppName(String pkg, Map<String, String> appLabels) {
  const names = {
    'com.cleanyourprojector.launcher': 'Home',
    'com.jixin.launcher5': 'Home',
    'com.jixin.settings5': 'Projector settings',
    'com.android.tv.settings': 'Settings',
    'com.jixin.btspeaker': 'Bluetooth',
    'com.jixin.wifi': 'Wi-Fi',
    'com.cleanyourprojector.remote': 'Remote pairing',
    'com.google.android.youtube.tv': 'YouTube',
    'org.videolan.vlc': 'VLC',
    'in.startv.hotstar': 'JioHotstar',
    'com.mxtech.videoplayer.ad': 'MX Player',
    'com.internet.tvbrowser': 'Browser',
    'com.android.vending': 'Play Store',
  };
  return names[pkg] ?? appLabels[pkg] ?? pkg.split('.').last;
}

class TransportBadge extends StatelessWidget {
  final TransportKind? kind;
  final bool connecting;
  const TransportBadge({super.key, required this.kind, required this.connecting});

  @override
  Widget build(BuildContext context) {
    final (label, color) = switch (kind) {
      TransportKind.wifi => ('Wi-Fi', Tokens.accent),
      TransportKind.ble => ('Bluetooth', Tokens.accent),
      null => (connecting ? 'Connecting' : 'Offline', connecting ? Tokens.muted : Tokens.warn),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.6)),
      ),
      child: Text(
        label,
        style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600),
      ),
    );
  }
}
