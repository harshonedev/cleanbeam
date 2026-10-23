import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../protocol/message.dart' show ShortcutItem;
import '../theme.dart';

/// Horizontal strip of projector/system screens the receiver can open ("Input source", "Wi-Fi", ...).
/// Hidden when the receiver reports none (older receiver, or nothing resolvable on that box).
class ShortcutsRow extends StatelessWidget {
  final List<ShortcutItem> items;
  final void Function(String id) onOpen;
  const ShortcutsRow({super.key, required this.items, required this.onOpen});

  static IconData iconFor(String id) => switch (id) {
    'input' => Icons.input_rounded,
    'wifi' => Icons.wifi_rounded,
    'bluetooth' => Icons.bluetooth_rounded,
    'focus' => Icons.center_focus_strong_rounded,
    'keystone' => Icons.aspect_ratio_rounded,
    'brightness' => Icons.brightness_6_rounded,
    'projection' => Icons.screen_rotation_alt_rounded,
    'projector_settings' => Icons.tune_rounded,
    'system_settings' => Icons.settings_rounded,
    _ => Icons.open_in_new_rounded,
  };

  @override
  Widget build(BuildContext context) {
    if (items.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.only(left: 4, bottom: 10),
          child: Text(
            'Quick settings',
            style: TextStyle(color: Tokens.muted, fontSize: 13, fontWeight: FontWeight.w600, letterSpacing: 0.2),
          ),
        ),
        SizedBox(
          height: 44,
          child: ListView.separated(
            key: const Key('shortcuts-row'),
            scrollDirection: Axis.horizontal,
            clipBehavior: Clip.none,
            itemCount: items.length,
            separatorBuilder: (_, _) => const SizedBox(width: 8),
            itemBuilder: (_, i) => _Chip(item: items[i], onTap: () => onOpen(items[i].id)),
          ),
        ),
      ],
    );
  }
}

class _Chip extends StatelessWidget {
  final ShortcutItem item;
  final VoidCallback onTap;
  const _Chip({required this.item, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      identifier: 'shortcut-${item.id}',
      child: Material(
        color: Tokens.raised,
        borderRadius: BorderRadius.circular(999),
        child: InkWell(
          key: Key('shortcut-${item.id}'),
          borderRadius: BorderRadius.circular(999),
          onTap: () {
            HapticFeedback.selectionClick();
            onTap();
          },
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(ShortcutsRow.iconFor(item.id), size: 18, color: Tokens.accent),
                const SizedBox(width: 8),
                Text(
                  item.label,
                  style: const TextStyle(color: Tokens.ink, fontSize: 14, fontWeight: FontWeight.w600),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
