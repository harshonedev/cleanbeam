import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/protocol/message.dart';

void main() {
  group('outgoing commands', () {
    test('key defaults to tap', () {
      expect(jsonEncode(key(20)), '{"t":"key","code":20,"action":"tap"}');
      expect(jsonEncode(key(23, action: 'down')), '{"t":"key","code":23,"action":"down"}');
    });
    test('auth, pair, text, mouse, click, scroll, launch, apps, power, status, ping', () {
      expect(jsonEncode(auth('TOKEN')), '{"t":"auth","token":"TOKEN"}');
      expect(jsonEncode(pair('482913')), '{"t":"pair","code":"482913"}');
      expect(jsonEncode(text('cricket')), '{"t":"text","s":"cricket"}');
      expect(jsonEncode(mouse(12, -4)), '{"t":"mouse","dx":12,"dy":-4}');
      expect(jsonEncode(click('left')), '{"t":"click","button":"left"}');
      expect(jsonEncode(scroll(3)), '{"t":"scroll","dy":3}');
      expect(jsonEncode(launch('org.videolan.vlc')), '{"t":"launch","pkg":"org.videolan.vlc"}');
      expect(jsonEncode(apps()), '{"t":"apps"}');
      expect(jsonEncode(power('sleep')), '{"t":"power","action":"sleep"}');
      expect(jsonEncode(status()), '{"t":"status"}');
      expect(jsonEncode(ping()), '{"t":"ping"}');
    });
  });

  group('parseEvent', () {
    test('shortcuts', () {
      final e = parseEvent('{"t":"shortcuts","items":[{"id":"input","label":"Input source","group":"Projector"},{"label":"no id"}]}');
      expect(e, isA<ShortcutsEvent>());
      final items = (e as ShortcutsEvent).items;
      expect(items.length, 1);
      expect(items.single.id, 'input');
      expect(items.single.label, 'Input source');
    });
    test('auth_ok and status carry the ip when present', () {
      final a = parseEvent('{"t":"auth_ok","name":"n","version":"1","ip":"192.168.10.56"}') as AuthOk;
      expect(a.ip, '192.168.10.56');
      final s = parseEvent('{"t":"status","app":null,"screenOn":true,"volume":1,"maxVolume":2,"ip":"192.168.10.56"}') as StatusEvent;
      expect(s.info.ip, '192.168.10.56');
      final old = parseEvent('{"t":"status","app":null,"screenOn":true,"volume":1,"maxVolume":2}') as StatusEvent;
      expect(old.info.ip, isNull);
    });
    test('open and shortcuts commands', () {
      expect(jsonEncode(shortcuts()), '{"t":"shortcuts"}');
      expect(jsonEncode(open('wifi')), '{"t":"open","id":"wifi"}');
    });
    test('auth_ok', () {
      final e = parseEvent('{"t":"auth_ok","name":"EGate Atom3X","version":"1.0"}');
      expect(e, isA<AuthOk>());
      expect((e as AuthOk).name, 'EGate Atom3X');
    });
    test('auth_fail and pair_fail', () {
      expect(parseEvent('{"t":"auth_fail"}'), isA<AuthFail>());
      expect(parseEvent('{"t":"pair_fail"}'), isA<PairFail>());
    });
    test('pair_ok carries token', () {
      final e = parseEvent('{"t":"pair_ok","token":"abc","name":"EGate Atom3X"}') as PairOk;
      expect(e.token, 'abc');
      expect(e.name, 'EGate Atom3X');
    });
    test('apps list', () {
      final e = parseEvent('{"t":"apps","items":[{"pkg":"com.google.android.youtube.tv","label":"YouTube"}]}') as AppsEvent;
      expect(e.items.single.pkg, 'com.google.android.youtube.tv');
      expect(e.items.single.label, 'YouTube');
    });
    test('status with null app', () {
      final e = parseEvent('{"t":"status","app":null,"screenOn":false,"volume":0,"maxVolume":15}') as StatusEvent;
      expect(e.info.app, isNull);
      expect(e.info.screenOn, false);
      expect(e.info.maxVolume, 15);
    });
    test('pong, err, unknown, malformed', () {
      expect(parseEvent('{"t":"pong"}'), isA<Pong>());
      expect((parseEvent('{"t":"err","msg":"x"}') as ErrEvent).msg, 'x');
      expect(parseEvent('{"t":"zzz"}'), isA<UnknownEvent>());
      expect(parseEvent('not json'), isA<UnknownEvent>());
    });
  });
}
