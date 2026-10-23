import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/protocol/chunker.dart';
import 'package:remote_app/protocol/message.dart';
import 'package:remote_app/transport/ble_gatt.dart';
import 'package:remote_app/transport/ble_transport.dart';
import 'package:remote_app/transport/transport.dart';

/// Fake GATT link that behaves like the projector's BLE server.
class FakeGatt implements BleGatt {
  final int mtuToGrant;
  final String token;
  final notifications = StreamController<Uint8List>.broadcast();
  final disconnects = StreamController<void>.broadcast();
  final writes = <Uint8List>[];
  final assembler = ChunkAssembler();
  final decoded = <String>[];
  bool connectedFlag = false;

  FakeGatt({this.mtuToGrant = 512, this.token = 'TOKEN'});

  @override
  Future<void> connect() async => connectedFlag = true;
  @override
  Future<int> requestMtu(int mtu) async => mtuToGrant;
  @override
  Stream<Uint8List> get evtNotifications => notifications.stream;
  @override
  Stream<void> get disconnected => disconnects.stream;
  @override
  Future<void> writeCmd(Uint8List chunk) async {
    writes.add(chunk);
    final msg = assembler.feed(chunk);
    if (msg == null) return;
    final s = utf8.decode(msg);
    decoded.add(s);
    final m = jsonDecode(s) as Map;
    if (m['t'] == 'auth') {
      reply(m['token'] == token ? '{"t":"auth_ok","name":"EGate Atom3X","version":"1.0","ip":"192.168.10.57"}' : '{"t":"auth_fail"}');
    } else if (m['t'] == 'apps') {
      final items = List.generate(40, (i) => '{"pkg":"com.example.app$i","label":"Application number $i"}').join(',');
      reply('{"t":"apps","items":[$items]}');
    }
  }

  void reply(String json) {
    for (final c in splitChunks(Uint8List.fromList(utf8.encode(json)), mtuToGrant)) {
      notifications.add(c);
    }
  }

  @override
  Future<void> disconnect() async => connectedFlag = false;
}

void main() {
  test('ble connect resolves on auth_ok', () async {
    final g = FakeGatt();
    final t = BleTransport(g, token: 'TOKEN');
    await t.connect();
    expect(t.kind, TransportKind.ble);
    expect(g.decoded.first, '{"t":"auth","token":"TOKEN"}');
  });

  test('ble connect records the projector ip from auth_ok', () async {
    final gatt = FakeGatt();
    final t = BleTransport(gatt, token: 'TOKEN');
    await t.connect();
    expect(t.projectorIp, '192.168.10.57');
    await t.close();
  });

  test('ble connect throws on auth_fail', () async {
    final g = FakeGatt(token: 'OTHER');
    final t = BleTransport(g, token: 'TOKEN');
    await expectLater(t.connect(), throwsA(isA<AuthFailed>()));
  });

  test('ble reassembles chunked apps event at small mtu', () async {
    final g = FakeGatt(mtuToGrant: 23);
    final t = BleTransport(g, token: 'TOKEN');
    final events = <Event>[];
    t.events.listen(events.add);
    await t.connect();
    t.send(apps());
    await Future.delayed(const Duration(milliseconds: 100));
    final e = events.whereType<AppsEvent>().single;
    expect(e.items.length, 40);
    expect(e.items.last.label, 'Application number 39');
    // auth (28 bytes) needed two chunks at mtu 23; apps (12 bytes) needed one.
    expect(g.writes.length, 3);
    expect(g.writes[0][0], 0x00);
    expect(g.writes[1][0], 0x81);
    expect(g.writes[2][0], 0x80);
  });

  test('ble sends 20 keys in order', () async {
    final g = FakeGatt();
    final t = BleTransport(g, token: 'TOKEN');
    await t.connect();
    for (var i = 0; i < 20; i++) {
      t.send(key(i));
    }
    await Future.delayed(const Duration(milliseconds: 100));
    expect(g.decoded.skip(1).toList(), List.generate(20, (i) => '{"t":"key","code":$i,"action":"tap"}'));
  });

  test('ble connected emits false on disconnect', () async {
    final g = FakeGatt();
    final t = BleTransport(g, token: 'TOKEN');
    final states = <bool>[];
    t.connected.listen(states.add);
    await t.connect();
    g.disconnects.add(null);
    await Future.delayed(const Duration(milliseconds: 50));
    expect(states, [true, false]);
  });
}
