import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/apps/favorites.dart';
import 'package:remote_app/protocol/message.dart';
import 'package:remote_app/ui/apps_screen.dart';

import 'fake_sink.dart';

class MemoryFavorites implements Favorites {
  final Set<String> pkgs;
  MemoryFavorites(this.pkgs);
  @override
  Future<Set<String>> load() async => Set.of(pkgs);
  @override
  Future<void> save(Set<String> p) async {
    pkgs
      ..clear()
      ..addAll(p);
  }
}

void main() {
  testWidgets('favourites render first and tap sends launch', (t) async {
    final sink = FakeSink();
    final apps = [
      const AppItem('com.a', 'Alpha'),
      const AppItem('com.b', 'Beta'),
      const AppItem('org.videolan.vlc', 'VLC'),
    ];
    await t.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AppsScreen(sink: sink, apps: apps, favorites: MemoryFavorites({'org.videolan.vlc'}), iconBase: null),
      ),
    ));
    await t.pumpAndSettle();
    final labels = t.widgetList<Text>(find.byType(Text)).map((w) => w.data).whereType<String>().toList();
    expect(labels.indexOf('VLC'), lessThan(labels.indexOf('Alpha')));
    await t.tap(find.text('Beta'));
    await t.pump();
    expect(sink.sent.last, {'t': 'launch', 'pkg': 'com.b'});
  });

  testWidgets('long-press toggles favourite', (t) async {
    final fav = MemoryFavorites({});
    await t.pumpWidget(MaterialApp(
      home: Scaffold(
        body: AppsScreen(sink: FakeSink(), apps: const [AppItem('com.a', 'Alpha')], favorites: fav, iconBase: null),
      ),
    ));
    await t.pumpAndSettle();
    await t.longPress(find.text('Alpha'));
    await t.pumpAndSettle();
    expect(fav.pkgs, {'com.a'});
  });
}
