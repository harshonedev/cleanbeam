import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/protocol/message.dart';
import 'package:remote_app/ui/remote_screen.dart';
import 'package:remote_app/ui/widgets/shortcuts_row.dart';

import 'fake_sink.dart';

void main() {
  testWidgets('row is hidden without items', (t) async {
    final sink = FakeSink();
    await t.pumpWidget(MaterialApp(home: Scaffold(body: RemoteScreen(sink: sink))));
    expect(find.byKey(const Key('shortcuts-row')), findsNothing);
    expect(find.text('Quick settings'), findsNothing);
  });

  testWidgets('tapping a shortcut sends open with its id', (t) async {
    final sink = FakeSink();
    const items = [ShortcutItem('input', 'Input source', 'Projector'), ShortcutItem('wifi', 'Wi-Fi', 'Network')];
    await t.pumpWidget(MaterialApp(home: Scaffold(body: RemoteScreen(sink: sink, shortcuts: items))));
    await t.scrollUntilVisible(find.byKey(const Key('shortcut-wifi')), 200, scrollable: find.byType(Scrollable).first);
    await t.tap(find.byKey(const Key('shortcut-wifi')));
    await t.pump();
    expect(sink.sent, [
      {'t': 'open', 'id': 'wifi'}
    ]);
  });

  test('every known id has a dedicated icon', () {
    for (final id in ['input', 'wifi', 'bluetooth', 'focus', 'keystone', 'brightness', 'projection', 'projector_settings', 'system_settings']) {
      expect(ShortcutsRow.iconFor(id), isNot(Icons.open_in_new_rounded), reason: id);
    }
  });
}
