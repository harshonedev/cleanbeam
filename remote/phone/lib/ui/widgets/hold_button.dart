import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../theme.dart';

enum HoldMode {
  /// Tap sends once. Holding repeats the tap every [repeatEvery] after [holdAfter].
  repeat,

  /// Tap sends once. Holding sends `down` at [holdAfter] and `up` on release.
  downUp,

  /// Tap only.
  tap,
}

/// A pressable area with haptics and press-and-hold behaviour.
class HoldButton extends StatefulWidget {
  final Widget child;
  final HoldMode mode;
  final VoidCallback onTap;
  final VoidCallback? onDown;
  final VoidCallback? onUp;
  final BorderRadius? radius;
  final Color? color;
  final double? size;
  final EdgeInsets padding;

  static const holdAfter = Duration(milliseconds: 400);
  static const repeatEvery = Duration(milliseconds: 120);

  const HoldButton({
    super.key,
    required this.child,
    required this.onTap,
    this.mode = HoldMode.tap,
    this.onDown,
    this.onUp,
    this.radius,
    this.color,
    this.size,
    this.padding = const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
  });

  @override
  State<HoldButton> createState() => _HoldButtonState();
}

class _HoldButtonState extends State<HoldButton> {
  Timer? _holdTimer;
  Timer? _repeatTimer;
  bool _pressed = false;
  bool _held = false;

  void _start(TapDownDetails _) {
    setState(() => _pressed = true);
    HapticFeedback.selectionClick();
    _held = false;
    switch (widget.mode) {
      case HoldMode.tap:
        break;
      case HoldMode.repeat:
        widget.onTap();
        _holdTimer = Timer(HoldButton.holdAfter, () {
          _held = true;
          _repeatTimer = Timer.periodic(HoldButton.repeatEvery, (_) => widget.onTap());
        });
      case HoldMode.downUp:
        _holdTimer = Timer(HoldButton.holdAfter, () {
          _held = true;
          widget.onDown?.call();
        });
    }
  }

  void _end({required bool cancelled}) {
    _holdTimer?.cancel();
    _repeatTimer?.cancel();
    if (mounted) setState(() => _pressed = false);
    switch (widget.mode) {
      case HoldMode.tap:
        if (!cancelled) widget.onTap();
      case HoldMode.repeat:
        break;
      case HoldMode.downUp:
        if (_held) {
          widget.onUp?.call();
        } else if (!cancelled) {
          widget.onTap();
        }
    }
    _held = false;
  }

  @override
  void dispose() {
    _holdTimer?.cancel();
    _repeatTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final radius = widget.radius ?? BorderRadius.circular(999);
    final color = widget.color ?? Tokens.raised;
    return Semantics(
      button: true,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: _start,
        onTapUp: (_) => _end(cancelled: false),
        onTapCancel: () => _end(cancelled: true),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 80),
          width: widget.size,
          height: widget.size,
          padding: widget.size == null ? widget.padding : EdgeInsets.zero,
          // Only a fixed-size button may align its child; an unconstrained Container with
          // `alignment` would expand to fill its parent.
          alignment: widget.size == null ? null : Alignment.center,
          decoration: BoxDecoration(
            color: _pressed ? Tokens.accentPressed : color,
            borderRadius: radius,
          ),
          child: DefaultTextStyle.merge(
            style: const TextStyle(color: Tokens.ink, fontSize: 15, fontWeight: FontWeight.w600),
            child: IconTheme.merge(data: const IconThemeData(color: Tokens.ink, size: 24), child: widget.child),
          ),
        ),
      ),
    );
  }
}
