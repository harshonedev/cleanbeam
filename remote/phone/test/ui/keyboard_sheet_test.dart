import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/ui/keyboard_sheet.dart';

import 'fake_sink.dart';

void main() {
  testWidgets('typing abc then backspace sends text a,b,c then key 67', (t) async {
    final sink = FakeSink();
    await t.pumpWidget(MaterialApp(home: Scaffold(body: KeyboardSheet(sink: sink))));
    final field = find.byType(TextField);
    await t.enterText(field, 'a');
    await t.enterText(field, 'ab');
    await t.enterText(field, 'abc');
    await t.enterText(field, 'ab');
    expect(sink.sent, [
      {'t': 'text', 's': 'a'},
      {'t': 'text', 's': 'b'},
      {'t': 'text', 's': 'c'},
      {'t': 'key', 'code': 67, 'action': 'tap'},
    ]);
  });

  testWidgets('submit sends enter', (t) async {
    final sink = FakeSink();
    await t.pumpWidget(MaterialApp(home: Scaffold(body: KeyboardSheet(sink: sink))));
    await t.enterText(find.byType(TextField), 'x');
    await t.testTextInput.receiveAction(TextInputAction.search);
    await t.pump();
    expect(sink.sent.last, {'t': 'key', 'code': 66, 'action': 'tap'});
  });
}
