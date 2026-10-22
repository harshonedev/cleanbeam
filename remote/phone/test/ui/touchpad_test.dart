import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/ui/touchpad_screen.dart';

import 'fake_sink.dart';

void main() {
  testWidgets('pan sends coalesced mouse deltas', (t) async {
    final sink = FakeSink();
    await t.pumpWidget(MaterialApp(home: Scaffold(body: TouchpadScreen(sink: sink))));
    final pad = find.byKey(const Key('touchpad'));
    final g = await t.startGesture(t.getCenter(pad));
    for (var i = 0; i < 10; i++) {
      await g.moveBy(const Offset(2, 1));
      await t.pump(const Duration(milliseconds: 2));
    }
    await t.pump(const Duration(milliseconds: 40));
    await g.up();
    await t.pump();
    final moves = sink.sent.where((m) => m['t'] == 'mouse').toList();
    expect(moves.length, lessThan(10));
    final dx = moves.fold<int>(0, (a, m) => a + (m['dx'] as int));
    final dy = moves.fold<int>(0, (a, m) => a + (m['dy'] as int));
    expect(dx, 30); // 20 px * 1.5
    expect(dy, 15);
    expect(sink.sent.where((m) => m['t'] == 'click'), isEmpty);
  });

  testWidgets('tap sends a left click', (t) async {
    final sink = FakeSink();
    await t.pumpWidget(MaterialApp(home: Scaffold(body: TouchpadScreen(sink: sink))));
    await t.tap(find.byKey(const Key('touchpad')));
    await t.pump(const Duration(milliseconds: 400));
    expect(sink.sent.last, {'t': 'click', 'button': 'left'});
  });
}
