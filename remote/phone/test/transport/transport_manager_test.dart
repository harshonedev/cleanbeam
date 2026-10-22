import 'dart:async';

import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/pairing/pairing.dart';
import 'package:remote_app/protocol/message.dart';
import 'package:remote_app/transport/transport.dart';
import 'package:remote_app/transport/transport_manager.dart';

/// Scripted transport: connect() resolves/throws after [delay].
class ScriptedTransport implements Transport {
  @override
  final TransportKind kind;
  final Duration delay;
  final bool succeed;
  final _events = StreamController<Event>.broadcast();
  final _connected = StreamController<bool>.broadcast();
  final sent = <Map<String, dynamic>>[];
  bool closed = false;
  int connectCalls = 0;

  ScriptedTransport(this.kind, {this.delay = Duration.zero, this.succeed = true});

  @override
  Stream<Event> get events => _events.stream;
  @override
  Stream<bool> get connected => _connected.stream;

  @override
  Future<void> connect() async {
    connectCalls++;
    await Future.delayed(delay);
    if (!succeed) throw TimeoutException('nope');
    _connected.add(true);
  }

  void drop() => _connected.add(false);
  void emit(Event e) => _events.add(e);

  @override
  void send(Map<String, dynamic> msg) => sent.add(msg);

  @override
  Future<void> close() async => closed = true;
}

class Harness {
  final pairing = Pairing(name: 'EGate Atom3X', ip: '192.168.10.52', port: 7272, token: 'TOKEN');
  ScriptedTransport Function() makeWs;
  ScriptedTransport Function() makeBle;
  final ws = <ScriptedTransport>[];
  final ble = <ScriptedTransport>[];
  String? mdnsIp;
  String? bleId;

  Harness({required this.makeWs, required this.makeBle, this.mdnsIp = '192.168.10.52', this.bleId = 'AA:BB'});

  TransportManager manager() => TransportManager(
        pairing: pairing,
        resolveMdns: (_) async => mdnsIp,
        makeWs: (_) => makeWs().also(ws.add),
        findBle: () async => bleId,
        makeBle: (_) => makeBle().also(ble.add),
      );
}

extension<T> on T {
  T also(void Function(T) f) {
    f(this);
    return this;
  }
}

void main() {
  test('prefers wifi when both succeed', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, delay: const Duration(milliseconds: 500)),
        makeBle: () => ScriptedTransport(TransportKind.ble, delay: const Duration(milliseconds: 100)),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(seconds: 1));
      expect(m.kind, TransportKind.wifi);
      expect(h.ble.single.closed, isTrue);
      m.stop();
    });
  });

  test('falls back to BLE when ws fails', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, delay: const Duration(seconds: 3), succeed: false),
        makeBle: () => ScriptedTransport(TransportKind.ble, delay: const Duration(seconds: 2)),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(seconds: 5));
      expect(m.kind, TransportKind.ble);
      expect(m.active, isNotNull);
      m.send(key(20));
      expect(h.ble.single.sent.first['t'], 'status'); // requested on attach
      expect(h.ble.single.sent.last['code'], 20);
      m.stop();
    });
  });

  test('switches to wifi if it arrives within 3s of a BLE win', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, delay: const Duration(milliseconds: 2500)),
        makeBle: () => ScriptedTransport(TransportKind.ble, delay: const Duration(milliseconds: 200)),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(milliseconds: 300));
      expect(m.kind, TransportKind.ble);
      async.elapse(const Duration(seconds: 3));
      expect(m.kind, TransportKind.wifi);
      expect(h.ble.single.closed, isTrue);
      m.stop();
    });
  });

  test('keeps BLE if wifi arrives later than 3s', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, delay: const Duration(seconds: 6)),
        makeBle: () => ScriptedTransport(TransportKind.ble, delay: const Duration(milliseconds: 200)),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(seconds: 8));
      expect(m.kind, TransportKind.ble);
      m.stop();
    });
  });

  test('reconnects with backoff after drop', () {
    fakeAsync((async) {
      var wsAttempts = 0;
      final h = Harness(
        makeWs: () {
          wsAttempts++;
          return ScriptedTransport(TransportKind.wifi, succeed: wsAttempts != 2);
        },
        makeBle: () => ScriptedTransport(TransportKind.ble, succeed: false),
        bleId: null,
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(milliseconds: 100));
      expect(m.kind, TransportKind.wifi);
      h.ws.first.drop();
      async.elapse(const Duration(milliseconds: 100));
      expect(m.kind, isNull);
      // attempt 2 after 1 s fails, attempt 3 after another 2 s succeeds
      async.elapse(const Duration(milliseconds: 1100));
      expect(wsAttempts, 2);
      expect(m.kind, isNull);
      async.elapse(const Duration(milliseconds: 2100));
      expect(wsAttempts, 3);
      expect(m.kind, TransportKind.wifi);
      m.stop();
    });
  });

  test('send without a connection records lastError', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, succeed: false),
        makeBle: () => ScriptedTransport(TransportKind.ble, succeed: false),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(milliseconds: 100));
      m.send(key(20));
      expect(m.lastError, isNotNull);
      m.stop();
    });
  });

  test('events from the active transport are forwarded', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi),
        makeBle: () => ScriptedTransport(TransportKind.ble, succeed: false),
      );
      final m = h.manager();
      final got = <Event>[];
      m.events.listen(got.add);
      m.start();
      async.elapse(const Duration(milliseconds: 100));
      h.ws.first.emit(const Pong());
      async.flushMicrotasks();
      expect(got.single, isA<Pong>());
      m.stop();
    });
  });
}
