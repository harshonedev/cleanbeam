import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/protocol/message.dart';
import 'package:remote_app/transport/transport.dart';
import 'package:remote_app/transport/ws_transport.dart';

/// A tiny stand-in for the projector's WebSocket server.
class FakeProjector {
  HttpServer? server;
  final received = <String>[];
  final sockets = <WebSocket>[];
  int get port => server!.port;

  Future<void> start({String token = 'TOKEN', String? pin}) async {
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    server!.listen((req) async {
      final ws = await WebSocketTransformer.upgrade(req);
      sockets.add(ws);
      var authed = false;
      ws.listen((data) {
        final s = data as String;
        received.add(s);
        final m = jsonDecode(s) as Map;
        if (!authed) {
          if (m['t'] == 'auth' && m['token'] == token) {
            authed = true;
            ws.add('{"t":"auth_ok","name":"EGate Atom3X","version":"1.0"}');
          } else if (m['t'] == 'pair' && pin != null && m['code'] == pin) {
            authed = true;
            ws.add('{"t":"pair_ok","token":"$token","name":"EGate Atom3X"}');
          } else {
            ws.add(m['t'] == 'pair' ? '{"t":"pair_fail"}' : '{"t":"auth_fail"}');
          }
          return;
        }
        if (m['t'] == 'ping') ws.add('{"t":"pong"}');
        if (m['t'] == 'apps') ws.add('{"t":"apps","items":[{"pkg":"a","label":"A"}]}');
      });
    });
  }

  Future<void> stop() async => server?.close(force: true);
}

void main() {
  late FakeProjector p;
  setUp(() async => p = FakeProjector());
  tearDown(() => p.stop());

  test('ws connect resolves on auth_ok', () async {
    await p.start();
    final t = WsTransport('127.0.0.1', p.port, token: 'TOKEN');
    await t.connect();
    expect(t.kind, TransportKind.wifi);
    expect(p.received.first, '{"t":"auth","token":"TOKEN"}');
    await t.close();
  });

  test('ws connect throws on auth_fail', () async {
    await p.start(token: 'OTHER');
    final t = WsTransport('127.0.0.1', p.port, token: 'TOKEN');
    await expectLater(t.connect(), throwsA(isA<AuthFailed>()));
    await t.close();
  });

  test('ws connect times out when nothing answers', () async {
    final silent = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
    final t = WsTransport('127.0.0.1', silent.port, token: 'TOKEN', timeout: const Duration(milliseconds: 300));
    await expectLater(t.connect(), throwsA(isA<TimeoutException>()));
    await t.close();
    await silent.close();
  });

  test('ws pairs with pin and exposes the token', () async {
    await p.start(pin: '482913');
    final t = WsTransport('127.0.0.1', p.port, pairCode: '482913');
    await t.connect();
    expect(t.pairedToken, 'TOKEN');
    expect(p.received.first, '{"t":"pair","code":"482913"}');
    await t.close();
  });

  test('ws delivers events and sends messages after auth', () async {
    await p.start();
    final t = WsTransport('127.0.0.1', p.port, token: 'TOKEN');
    final events = <Event>[];
    t.events.listen(events.add);
    await t.connect();
    t.send(apps());
    await Future.delayed(const Duration(milliseconds: 200));
    expect(events.whereType<AppsEvent>().single.items.single.label, 'A');
    await t.close();
  });

  test('ws connected emits false when the server drops the socket', () async {
    await p.start();
    final t = WsTransport('127.0.0.1', p.port, token: 'TOKEN');
    final states = <bool>[];
    t.connected.listen(states.add);
    await t.connect();
    await Future.delayed(const Duration(milliseconds: 50));
    for (final s in p.sockets) {
      await s.close();
    }
    await Future.delayed(const Duration(milliseconds: 200));
    expect(states, [true, false]);
    await t.close();
  });
}
