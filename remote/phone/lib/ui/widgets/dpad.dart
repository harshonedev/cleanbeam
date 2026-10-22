import 'package:flutter/material.dart';

import '../../protocol/message.dart' as msg;
import '../theme.dart';
import 'hold_button.dart';

/// The one bold element: a large ring with four directions and OK in the middle.
class DPad extends StatelessWidget {
  final void Function(Map<String, dynamic>) send;
  final double diameter;

  const DPad({super.key, required this.send, this.diameter = 280});

  static const up = 19, down = 20, left = 21, right = 22, center = 23;

  @override
  Widget build(BuildContext context) {
    final arm = diameter * 0.30;
    final centerSize = diameter * 0.36;
    return SizedBox(
      width: diameter,
      height: diameter,
      child: Stack(
        alignment: Alignment.center,
        children: [
          Container(
            width: diameter,
            height: diameter,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: Tokens.surface,
              border: Border.all(color: Tokens.raised, width: 2),
            ),
          ),
          _arm('dpad-up', Icons.keyboard_arrow_up_rounded, up, Alignment.topCenter, arm, diameter),
          _arm('dpad-down', Icons.keyboard_arrow_down_rounded, down, Alignment.bottomCenter, arm, diameter),
          _arm('dpad-left', Icons.keyboard_arrow_left_rounded, left, Alignment.centerLeft, arm, diameter),
          _arm('dpad-right', Icons.keyboard_arrow_right_rounded, right, Alignment.centerRight, arm, diameter),
          HoldButton(
            key: const Key('dpad-ok'),
            mode: HoldMode.downUp,
            size: centerSize,
            color: Tokens.accent,
            onTap: () => send(msg.key(center)),
            onDown: () => send(msg.key(center, action: 'down')),
            onUp: () => send(msg.key(center, action: 'up')),
            child: const Text('OK', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700, letterSpacing: 1)),
          ),
        ],
      ),
    );
  }

  Widget _arm(String keyName, IconData icon, int code, Alignment align, double arm, double d) {
    final vertical = align == Alignment.topCenter || align == Alignment.bottomCenter;
    return Align(
      alignment: align,
      child: HoldButton(
        key: Key(keyName),
        mode: HoldMode.repeat,
        color: Colors.transparent,
        radius: BorderRadius.zero,
        padding: EdgeInsets.zero,
        onTap: () => send(msg.key(code)),
        child: SizedBox(
          width: vertical ? d * 0.5 : arm,
          height: vertical ? arm : d * 0.5,
          child: Icon(icon, size: 44, color: Tokens.ink),
        ),
      ),
    );
  }
}
