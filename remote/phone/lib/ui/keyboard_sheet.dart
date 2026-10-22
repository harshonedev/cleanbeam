import 'package:flutter/material.dart';

import '../protocol/message.dart' as msg;
import '../transport/command_sink.dart';
import 'theme.dart';

Future<void> showKeyboardSheet(BuildContext context, CommandSink sink) {
  return showModalBottomSheet(
    context: context,
    backgroundColor: Tokens.surface,
    isScrollControlled: true,
    builder: (ctx) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(ctx).bottom),
      child: KeyboardSheet(sink: sink),
    ),
  );
}

/// Sends each keystroke as it happens so the projector's search box updates live.
class KeyboardSheet extends StatefulWidget {
  final CommandSink sink;
  const KeyboardSheet({super.key, required this.sink});

  @override
  State<KeyboardSheet> createState() => _KeyboardSheetState();
}

class _KeyboardSheetState extends State<KeyboardSheet> {
  String _last = '';

  void _changed(String now) {
    var prefix = 0;
    while (prefix < _last.length && prefix < now.length && _last[prefix] == now[prefix]) {
      prefix++;
    }
    for (var i = prefix; i < _last.length; i++) {
      widget.sink.send(msg.key(67));
    }
    for (final ch in now.substring(prefix).characters) {
      widget.sink.send(msg.text(ch));
    }
    _last = now;
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Type on the projector', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
          const SizedBox(height: 4),
          const Text('Each key goes straight to the screen. Enter submits.', style: TextStyle(color: Tokens.muted, fontSize: 13)),
          const SizedBox(height: 14),
          TextField(
            autofocus: true,
            autocorrect: false,
            enableSuggestions: false,
            textInputAction: TextInputAction.search,
            onChanged: _changed,
            onSubmitted: (_) => widget.sink.send(msg.key(66)),
            style: const TextStyle(fontSize: 18),
            decoration: InputDecoration(
              filled: true,
              fillColor: Tokens.raised,
              hintText: 'Search…',
              hintStyle: const TextStyle(color: Tokens.muted),
              border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
            ),
          ),
        ],
      ),
    );
  }
}
