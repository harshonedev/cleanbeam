import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../protocol/message.dart' as msg;
import '../transport/command_sink.dart';
import 'theme.dart';
import 'widgets/hold_button.dart';

Future<void> showKeyboardSheet(BuildContext context, CommandSink sink) {
  return showModalBottomSheet(
    context: context,
    backgroundColor: Tokens.surface,
    isScrollControlled: true,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
    builder: (ctx) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(ctx).bottom),
      child: KeyboardSheet(sink: sink),
    ),
  );
}

/// Sends each keystroke as it happens so the projector's field updates live.
///
/// The phone's text field mirrors what we have typed on the projector since the sheet opened;
/// the diff between the previous and current value becomes backspaces and typed characters.
/// Backspace, Clear and Paste are explicit buttons so they work the same with every keyboard app.
class KeyboardSheet extends StatefulWidget {
  final CommandSink sink;
  const KeyboardSheet({super.key, required this.sink});

  /// One `text` message carries at most this many characters (receiver refuses longer ones).
  static const pasteChunk = 400;

  @override
  State<KeyboardSheet> createState() => _KeyboardSheetState();
}

class _KeyboardSheetState extends State<KeyboardSheet> {
  final _controller = TextEditingController();
  final _focus = FocusNode();
  String _last = '';
  bool _muted = false; // true while we change the field ourselves

  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _changed(String now) {
    if (_muted) return;
    final old = _last.characters;
    final cur = now.characters;
    var prefix = 0;
    while (prefix < old.length && prefix < cur.length && old.elementAt(prefix) == cur.elementAt(prefix)) {
      prefix++;
    }
    for (var i = prefix; i < old.length; i++) {
      widget.sink.send(msg.key(67));
    }
    final added = cur.skip(prefix).join();
    if (added.isNotEmpty) _sendText(added);
    _last = now;
  }

  void _sendText(String s) {
    // Grapheme by grapheme while typing (the receiver types mappable characters and pastes the
    // rest); whole chunks for pasted text so a long paste is a few messages, not hundreds.
    if (s.characters.length <= 4) {
      for (final ch in s.characters) {
        widget.sink.send(msg.text(ch));
      }
      return;
    }
    var rest = s;
    while (rest.isNotEmpty) {
      final take = rest.characters.take(KeyboardSheet.pasteChunk).join();
      widget.sink.send(msg.text(take));
      rest = rest.substring(take.length);
    }
  }

  void _setField(String value) {
    _muted = true;
    _controller.value = TextEditingValue(text: value, selection: TextSelection.collapsed(offset: value.length));
    _muted = false;
    _last = value;
  }

  void _backspace() {
    widget.sink.send(msg.key(67));
    final chars = _last.characters;
    if (chars.isNotEmpty) _setField(chars.skipLast(1).join());
  }

  void _clear() {
    final n = _last.characters.length;
    if (n == 0) return;
    HapticFeedback.mediumImpact();
    for (var i = 0; i < n; i++) {
      widget.sink.send(msg.key(67));
    }
    _setField('');
  }

  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    final s = data?.text ?? '';
    if (s.isEmpty) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Clipboard is empty')));
      return;
    }
    HapticFeedback.selectionClick();
    _sendText(s);
    _setField(_last + s);
  }

  void _enter() {
    HapticFeedback.selectionClick();
    widget.sink.send(msg.key(66));
  }

  @override
  Widget build(BuildContext context) {
    final connected = widget.sink.kind != null;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 14, 20, 16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Center(
            child: Container(
              width: 36,
              height: 4,
              margin: const EdgeInsets.only(bottom: 14),
              decoration: BoxDecoration(color: Tokens.raised, borderRadius: BorderRadius.circular(2)),
            ),
          ),
          Row(
            children: [
              const Expanded(
                child: Text('Type on the projector', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
              ),
              _LiveBadge(connected: connected),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            connected
                ? 'Every key goes straight to the field that has focus on the projector.'
                : 'Not connected. Keys will be dropped until the projector answers.',
            style: TextStyle(color: connected ? Tokens.muted : Tokens.warn, fontSize: 13, height: 1.35),
          ),
          const SizedBox(height: 14),
          TextField(
            key: const Key('type-field'),
            controller: _controller,
            focusNode: _focus,
            autofocus: true,
            autocorrect: false,
            enableSuggestions: false,
            keyboardType: TextInputType.text,
            textInputAction: TextInputAction.send,
            onChanged: _changed,
            onSubmitted: (_) => _enter(),
            style: const TextStyle(fontSize: 18),
            decoration: InputDecoration(
              filled: true,
              fillColor: Tokens.raised,
              hintText: 'Type here…',
              hintStyle: const TextStyle(color: Tokens.muted),
              contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
            ),
          ),
          const SizedBox(height: 12),
          Row(
            children: [
              HoldButton(
                key: const Key('type-backspace'),
                mode: HoldMode.repeat,
                onTap: _backspace,
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                child: const Icon(Icons.backspace_outlined, size: 22),
              ),
              const SizedBox(width: 8),
              HoldButton(
                key: const Key('type-clear'),
                onTap: _clear,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                child: const Text('Clear'),
              ),
              const SizedBox(width: 8),
              HoldButton(
                key: const Key('type-paste'),
                onTap: _paste,
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                child: const Row(mainAxisSize: MainAxisSize.min, children: [Icon(Icons.content_paste_rounded, size: 18), SizedBox(width: 6), Text('Paste')]),
              ),
              const SizedBox(width: 8),
              // Takes whatever width is left, so the row fits 360 dp phones without overflowing.
              Expanded(
                child: HoldButton(
                  key: const Key('type-enter'),
                  onTap: _enter,
                  color: Tokens.accent,
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
                  child: const Row(
                    mainAxisSize: MainAxisSize.min,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [Text('Enter'), SizedBox(width: 6), Icon(Icons.keyboard_return_rounded, size: 20)],
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _LiveBadge extends StatelessWidget {
  final bool connected;
  const _LiveBadge({required this.connected});

  @override
  Widget build(BuildContext context) {
    final color = connected ? Tokens.accent : Tokens.warn;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(width: 8, height: 8, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: 6),
        Text(connected ? 'Live' : 'Offline', style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600)),
      ],
    );
  }
}
