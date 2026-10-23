import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/ui/keyboard_sheet.dart';

import 'fake_sink.dart';

void main() {
  Future<FakeSink> pump(WidgetTester t) async {
    final sink = FakeSink();
    await t.pumpWidget(MaterialApp(home: Scaffold(body: KeyboardSheet(sink: sink))));
    return sink;
  }

  final field = find.byKey(const Key('type-field'));

  testWidgets('typing abc then backspace sends text a,b,c then key 67', (t) async {
    final sink = await pump(t);
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

  testWidgets('an emoji is one character: one text message, one backspace', (t) async {
    final sink = await pump(t);
    await t.enterText(field, '😀');
    await t.enterText(field, '');
    expect(sink.sent, [
      {'t': 'text', 's': '😀'},
      {'t': 'key', 'code': 67, 'action': 'tap'},
    ]);
  });

  testWidgets('keyboard-app word replacement becomes backspaces then the new tail', (t) async {
    final sink = await pump(t);
    await t.enterText(field, 'helo');
    sink.sent.clear();
    await t.enterText(field, 'hello');
    expect(sink.sent, [
      {'t': 'key', 'code': 67, 'action': 'tap'},
      {'t': 'text', 's': 'l'},
      {'t': 'text', 's': 'o'},
    ]);
  });

  testWidgets('submit and the Enter button send key 66', (t) async {
    final sink = await pump(t);
    await t.enterText(field, 'x');
    await t.testTextInput.receiveAction(TextInputAction.send);
    await t.pump();
    expect(sink.sent.last, {'t': 'key', 'code': 66, 'action': 'tap'});
    await t.tap(find.byKey(const Key('type-enter')));
    await t.pump();
    expect(sink.sent.last, {'t': 'key', 'code': 66, 'action': 'tap'});
  });

  testWidgets('backspace button deletes on both sides; clear removes everything typed', (t) async {
    final sink = await pump(t);
    await t.enterText(field, 'abc');
    sink.sent.clear();
    await t.tap(find.byKey(const Key('type-backspace')));
    await t.pump();
    expect(sink.sent, [
      {'t': 'key', 'code': 67, 'action': 'tap'}
    ]);
    expect(t.widget<TextField>(field).controller!.text, 'ab');
    sink.sent.clear();
    await t.tap(find.byKey(const Key('type-clear')));
    await t.pump();
    expect(sink.sent.length, 2);
    expect(sink.sent.every((m) => m['code'] == 67), isTrue);
    expect(t.widget<TextField>(field).controller!.text, '');
    sink.sent.clear();
    await t.tap(find.byKey(const Key('type-clear')));
    await t.pump();
    expect(sink.sent, isEmpty); // nothing of ours left to delete
  });

  testWidgets('button row fits a 360 dp wide phone without overflowing', (t) async {
    t.view.physicalSize = const Size(360, 640);
    t.view.devicePixelRatio = 1.0;
    addTearDown(t.view.reset);
    final sink = FakeSink();
    await t.pumpWidget(MaterialApp(home: Scaffold(body: KeyboardSheet(sink: sink))));
    expect(t.takeException(), isNull); // a RenderFlex overflow would be reported here
    expect(find.byKey(const Key('type-enter')), findsOneWidget);
  });

  testWidgets('paste sends the clipboard in chunks and shows it in the field', (t) async {
    final sink = await pump(t);
    final long = List.filled(KeyboardSheet.pasteChunk + 5, 'x').join();
    TestWidgetsFlutterBinding.ensureInitialized();
    t.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.getData') return {'text': long};
      return null;
    });
    await t.tap(find.byKey(const Key('type-paste')));
    await t.pump();
    expect(sink.sent.length, 2);
    expect(sink.sent[0]['s'], long.substring(0, KeyboardSheet.pasteChunk));
    expect(sink.sent[1]['s'], 'xxxxx');
    expect(t.widget<TextField>(field).controller!.text, long);
    // typing after a paste continues from the pasted text
    sink.sent.clear();
    await t.enterText(field, '${long}y');
    expect(sink.sent, [
      {'t': 'text', 's': 'y'}
    ]);
    t.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null);
  });
}
