import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../protocol/message.dart' as msg;
import '../transport/command_sink.dart';
import 'theme.dart';

/// Drag = move pointer, tap = click, two fingers up/down = scroll.
class TouchpadScreen extends StatefulWidget {
  final CommandSink sink;
  const TouchpadScreen({super.key, required this.sink});

  static const gain = 1.5;
  static const flushEvery = Duration(milliseconds: 16);

  @override
  State<TouchpadScreen> createState() => _TouchpadScreenState();
}

class _TouchpadScreenState extends State<TouchpadScreen> {
  double _ax = 0, _ay = 0, _ascroll = 0;
  Timer? _flush;
  final _pointers = <int, Offset>{};
  Offset? _downAt;
  DateTime? _downTime;
  bool _moved = false;

  void _schedule() => _flush ??= Timer(TouchpadScreen.flushEvery, _send);

  void _send() {
    _flush = null;
    final dx = _ax.truncate(), dy = _ay.truncate();
    if (dx != 0 || dy != 0) {
      _ax -= dx;
      _ay -= dy;
      widget.sink.send(msg.mouse(dx, dy));
    }
    final ds = _ascroll.truncate();
    if (ds != 0) {
      _ascroll -= ds;
      widget.sink.send(msg.scroll(ds));
    }
  }

  void _down(PointerDownEvent e) {
    _pointers[e.pointer] = e.position;
    if (_pointers.length == 1) {
      _downAt = e.position;
      _downTime = DateTime.now();
      _moved = false;
    }
  }

  void _move(PointerMoveEvent e) {
    final prev = _pointers[e.pointer];
    _pointers[e.pointer] = e.position;
    if (prev == null) return;
    final d = e.position - prev;
    if (_pointers.length >= 2) {
      _ascroll += d.dy / 20;
    } else {
      _ax += d.dx * TouchpadScreen.gain;
      _ay += d.dy * TouchpadScreen.gain;
    }
    if ((e.position - (_downAt ?? e.position)).distance > 8) _moved = true;
    _schedule();
  }

  void _up(PointerEvent e) {
    _pointers.remove(e.pointer);
    _flush?.cancel();
    _send();
    if (_pointers.isEmpty && !_moved && _downTime != null && DateTime.now().difference(_downTime!) < const Duration(milliseconds: 300)) {
      HapticFeedback.selectionClick();
      widget.sink.send(msg.click('left'));
    }
    if (_pointers.isEmpty) _downTime = null;
  }

  @override
  void dispose() {
    _flush?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
      child: Column(
        children: [
          Expanded(
            child: Listener(
              key: const Key('touchpad'),
              behavior: HitTestBehavior.opaque,
              onPointerDown: _down,
              onPointerMove: _move,
              onPointerUp: _up,
              onPointerCancel: _up,
              child: Container(
                decoration: BoxDecoration(
                  color: Tokens.surface,
                  borderRadius: BorderRadius.circular(28),
                  border: Border.all(color: Tokens.raised, width: 2),
                ),
                alignment: Alignment.center,
                child: const Text('Drag to move, tap to click,\ntwo fingers to scroll',
                    textAlign: TextAlign.center, style: TextStyle(color: Tokens.muted, height: 1.5)),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              Expanded(child: _button('Left click', () => widget.sink.send(msg.click('left')))),
              const SizedBox(width: 12),
              Expanded(child: _button('Right click', () => widget.sink.send(msg.click('right')))),
            ],
          ),
        ],
      ),
    );
  }

  Widget _button(String label, VoidCallback onTap) => GestureDetector(
        onTap: () {
          HapticFeedback.selectionClick();
          onTap();
        },
        child: Container(
          height: 56,
          alignment: Alignment.center,
          decoration: BoxDecoration(color: Tokens.raised, borderRadius: BorderRadius.circular(18)),
          child: Text(label, style: const TextStyle(fontWeight: FontWeight.w600)),
        ),
      );
}
