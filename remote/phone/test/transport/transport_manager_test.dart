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
  final bool rejectAuth;
  @override
  final String? projectorIp;
  final _events = StreamController<Event>.broadcast();
  final _connected = StreamController<bool>.broadcast();
  final sent = <Map<String, dynamic>>[];
  bool closed = false;
  int connectCalls = 0;

  ScriptedTransport(this.kind, {this.delay = Duration.zero, this.succeed = true, this.rejectAuth = false, this.projectorIp});

  @override
  Stream<Event> get events => _events.stream;
  @override
  Stream<bool> get connected => _connected.stream;

  @override
  Future<void> connect() async {
    connectCalls++;
    await Future.delayed(delay);
    if (rejectAuth) throw const AuthFailed();
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
  final wsHosts = <String>[];
  final ble = <ScriptedTransport>[];
  final learned = <String>[];
  String? mdnsIp;
  String? bleId;
  int scans = 0;

  Harness({required this.makeWs, required this.makeBle, this.mdnsIp = '192.168.10.52', this.bleId = 'AA:BB'});

  TransportManager manager({String? initialLearnedIp}) => TransportManager(
        pairing: pairing,
        learnedIp: initialLearnedIp,
        resolveMdns: (_) async => mdnsIp,
        makeWs: (host) {
          wsHosts.add(host);
          return makeWs().also(ws.add);
        },
        findBle: () async {
          scans++;
          return bleId;
        },
        makeBle: (_) => makeBle().also(ble.add),
        onIpLearned: learned.add,
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

  test('learns the projector ip from status over BLE and moves to wifi at that ip', () {
    fakeAsync((async) {
      var wsAttempts = 0;
      final h = Harness(
        // saved ip is stale: wifi fails until the learned ip is used
        makeWs: () {
          wsAttempts++;
          return ScriptedTransport(TransportKind.wifi, succeed: wsAttempts > 1);
        },
        makeBle: () => ScriptedTransport(TransportKind.ble, delay: const Duration(milliseconds: 200)),
        mdnsIp: null,
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(seconds: 1));
      expect(m.kind, TransportKind.ble);
      expect(h.wsHosts, ['192.168.10.52']);
      h.ble.first.emit(const StatusEvent(StatusInfo(screenOn: true, volume: 1, maxVolume: 2, ip: '192.168.10.56')));
      async.elapse(const Duration(milliseconds: 10));
      expect(h.learned, ['192.168.10.56']);
      // a new address is tried at once, not after the 15 s retry tick
      async.elapse(const Duration(milliseconds: 200));
      expect(h.wsHosts.last, '192.168.10.56');
      expect(m.kind, TransportKind.wifi);
      expect(h.ble.first.closed, isTrue);
      expect(m.activeHost, '192.168.10.56');
      m.stop();
    });
  });

  test('keeps retrying wifi while on BLE and stays on BLE when it keeps failing', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, succeed: false),
        makeBle: () => ScriptedTransport(TransportKind.ble),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(seconds: 1));
      expect(m.kind, TransportKind.ble);
      final before = h.ws.length;
      async.elapse(TransportManager.wifiRetryWhileOnBle * 2 + const Duration(seconds: 1));
      expect(h.ws.length, greaterThan(before));
      expect(m.kind, TransportKind.ble);
      expect(h.ble.single.closed, isFalse);
      m.stop();
    });
  });

  test('a learned ip beats the saved one but mdns beats both', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, succeed: false),
        makeBle: () => ScriptedTransport(TransportKind.ble, succeed: false),
        bleId: null,
        mdnsIp: null,
      );
      final m = h.manager(initialLearnedIp: '192.168.10.60');
      m.start();
      async.elapse(const Duration(milliseconds: 100));
      expect(h.wsHosts, ['192.168.10.60']);
      h.mdnsIp = '192.168.10.70';
      async.elapse(const Duration(seconds: 2));
      expect(h.wsHosts.last, '192.168.10.70');
      m.stop();
    });
  });

  test('a rejected token stops the retry loop and is reported', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, rejectAuth: true),
        makeBle: () => ScriptedTransport(TransportKind.ble, rejectAuth: true),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(milliseconds: 100));
      expect(m.authFailed, isTrue);
      expect(m.kind, isNull);
      expect(m.lastError, contains('no longer accepts'));
      final attempts = h.ws.length + h.ble.length;
      async.elapse(const Duration(seconds: 60));
      expect(h.ws.length + h.ble.length, attempts); // no more attempts
      m.stop();
      m.start(); // explicit reconnect tries again
      async.elapse(const Duration(milliseconds: 100));
      expect(h.ws.length + h.ble.length, greaterThan(attempts));
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

  test('activeHost is the host the winning wifi transport used', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi),
        makeBle: () => ScriptedTransport(TransportKind.ble, succeed: false),
        mdnsIp: '192.168.10.99',
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(milliseconds: 100));
      expect(m.activeHost, '192.168.10.99');
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

  test('a restart waits for the previous round\'s BLE attempt before touching the device again', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, succeed: false),
        makeBle: () => ScriptedTransport(TransportKind.ble, delay: const Duration(seconds: 3)),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(seconds: 1));
      expect(h.ble.length, 1);
      m.stop();
      m.start(); // "Reconnect" while the first BLE connect is still in flight
      async.elapse(const Duration(seconds: 1));
      // the device is busy with attempt 1: no second connect() on it yet
      expect(h.ble.length, 1);
      async.elapse(const Duration(seconds: 1200));
      expect(h.ble.first.closed, isTrue); // the stale attempt was discarded
      expect(h.ble.length, 2);
      expect(m.kind, TransportKind.ble);
      expect(m.active, same(h.ble.last));
      m.stop();
    });
  });

  test('resumed() drops a link that does not answer a ping within 2 s', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi),
        makeBle: () => ScriptedTransport(TransportKind.ble, succeed: false),
        bleId: null,
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(milliseconds: 100));
      expect(m.kind, TransportKind.wifi);
      final ws = h.ws.single;
      m.resumed();
      expect(ws.sent.last['t'], 'ping');
      async.elapse(const Duration(milliseconds: 2100));
      expect(ws.closed, isTrue);
      expect(m.lastError, 'connection lost');
      async.elapse(const Duration(seconds: 2));
      expect(h.ws.length, 2); // reconnected
      m.stop();
    });
  });

  test('resumed() keeps a link that answers the ping', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi),
        makeBle: () => ScriptedTransport(TransportKind.ble, succeed: false),
        bleId: null,
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(milliseconds: 100));
      final ws = h.ws.single;
      m.resumed();
      async.elapse(const Duration(milliseconds: 500));
      ws.emit(const Pong());
      async.elapse(const Duration(seconds: 3));
      expect(ws.closed, isFalse);
      expect(m.kind, TransportKind.wifi);
      expect(h.ws.length, 1);
      m.stop();
    });
  });

  test('keeps BLE scans under five per 30 s while nothing answers (Android scan throttle)', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, succeed: false),
        makeBle: () => ScriptedTransport(TransportKind.ble, succeed: false),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(seconds: 30));
      expect(h.scans, lessThanOrEqualTo(5));
      final at30 = h.scans;
      async.elapse(const Duration(seconds: 30));
      expect(h.scans - at30, lessThanOrEqualTo(5));
      expect(h.scans, greaterThan(at30)); // still retrying
      m.stop();
    });
  });

  test('a wifi win from the opening round does not replace a wifi link the upgrade already adopted', () {
    fakeAsync((async) {
      var wsCalls = 0;
      final h = Harness(
        // round's own attempt is slow; the upgrade triggered by the learned ip connects at once
        makeWs: () => ScriptedTransport(TransportKind.wifi, delay: wsCalls++ == 0 ? const Duration(milliseconds: 2500) : Duration.zero),
        makeBle: () => ScriptedTransport(TransportKind.ble, delay: const Duration(milliseconds: 100)),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(milliseconds: 200));
      expect(m.kind, TransportKind.ble);
      h.ble.single.emit(const StatusEvent(StatusInfo(screenOn: true, volume: 1, maxVolume: 2, ip: '192.168.10.57')));
      async.elapse(const Duration(milliseconds: 100));
      expect(m.kind, TransportKind.wifi);
      final adopted = m.active;
      async.elapse(const Duration(seconds: 3));
      expect(m.active, same(adopted));
      expect(h.ws.where((w) => !w.closed).length, 1);
      m.stop();
    });
  });

  test('the ip the transport saw in auth_ok is learned on attach', () {
    fakeAsync((async) {
      final h = Harness(
        makeWs: () => ScriptedTransport(TransportKind.wifi, succeed: false),
        makeBle: () => ScriptedTransport(TransportKind.ble, projectorIp: '192.168.10.58'),
      );
      final m = h.manager();
      m.start();
      async.elapse(const Duration(milliseconds: 100));
      expect(m.kind, TransportKind.ble);
      expect(h.learned, ['192.168.10.58']);
      m.stop();
    });
  });
}
