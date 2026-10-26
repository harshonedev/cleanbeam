import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/ui/remote_screen.dart';

import 'fake_sink.dart';

void main() {
  StatusNameTests.run();
  Future<FakeSink> pump(WidgetTester t) async {
    final sink = FakeSink();
    await t.pumpWidget(
      MaterialApp(
        home: Scaffold(body: RemoteScreen(sink: sink)),
      ),
    );
    return sink;
  }

  testWidgets('tapping OK sends key 23 tap', (t) async {
    final sink = await pump(t);
    await t.tap(find.byKey(const Key('dpad-ok')));
    await t.pump();
    expect(sink.sent, [
      {'t': 'key', 'code': 23, 'action': 'tap'},
    ]);
  });

  testWidgets('long-press OK sends down then up', (t) async {
    final sink = await pump(t);
    final g = await t.startGesture(t.getCenter(find.byKey(const Key('dpad-ok'))));
    await t.pump(const Duration(milliseconds: 700));
    await g.up();
    await t.pump();
    expect(sink.sent.map((m) => m['action']).toList(), ['down', 'up']);
    expect(sink.sent.every((m) => m['code'] == 23), isTrue);
  });

  testWidgets('direction buttons send the right keycodes', (t) async {
    final sink = await pump(t);
    for (final e in {'dpad-up': 19, 'dpad-down': 20, 'dpad-left': 21, 'dpad-right': 22}.entries) {
      await t.tap(find.byKey(Key(e.key)));
      await t.pump();
      expect(sink.sent.last, {'t': 'key', 'code': e.value, 'action': 'tap'});
    }
  });

  testWidgets('back home menu volume media and power buttons', (t) async {
    final sink = await pump(t);
    final expected = {
      'btn-back': 4,
      'btn-home': 3,
      'btn-menu': 82,
      'btn-vol-down': 25,
      'btn-mute': 164,
      'btn-vol-up': 24,
      'btn-rew': 89,
      'btn-play': 85,
      'btn-ffwd': 90,
    };
    for (final e in expected.entries) {
      await t.ensureVisible(find.byKey(Key(e.key)));
      await t.tap(find.byKey(Key(e.key)));
      await t.pump();
      expect(sink.sent.last, {'t': 'key', 'code': e.value, 'action': 'tap'}, reason: e.key);
    }
    await t.ensureVisible(find.byKey(const Key('btn-power-menu')));
    await t.tap(find.byKey(const Key('btn-power-menu')));
    await t.pump();
    expect(sink.sent.last, {'t': 'power', 'action': 'menu'});
  });

  testWidgets('no wake button is shown', (t) async {
    await pump(t);
    expect(find.byKey(const Key('btn-wake')), findsNothing);
  });
}

class StatusNameTests {
  static void run() {
    test('status line names: system map, then receiver labels, then package tail', () {
      expect(friendlyAppName('com.jixin.settings5', {'com.jixin.settings5': 'More Apps'}), 'Projector settings');
      expect(friendlyAppName('ahaflix.tv', {'ahaflix.tv': 'aha'}), 'aha');
      expect(friendlyAppName('com.example.thing', {}), 'thing');
    });
  }
}
