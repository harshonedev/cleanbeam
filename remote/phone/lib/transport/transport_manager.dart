import 'dart:async';

import 'package:flutter/foundation.dart';

import '../pairing/pairing.dart';
import '../protocol/message.dart';
import 'command_sink.dart';
import 'transport.dart';

/// Races Wi-Fi and BLE, prefers Wi-Fi, reconnects with backoff.
class TransportManager extends ChangeNotifier implements CommandSink {
  final Pairing pairing;
  final Future<String?> Function(String name) resolveMdns;
  final Transport Function(String host) makeWs;
  final Future<String?> Function() findBle;
  final Transport Function(String id) makeBle;

  static const mdnsTimeout = Duration(seconds: 2);
  static const wifiGrace = Duration(seconds: 3);
  static const backoff = [Duration(seconds: 1), Duration(seconds: 2), Duration(seconds: 4), Duration(seconds: 8)];

  Transport? _active;
  String? _activeHost;
  StreamSubscription? _eventSub;
  StreamSubscription? _stateSub;
  final _events = StreamController<Event>.broadcast();
  bool _started = false;
  bool _connecting = false;
  int _attempt = 0;
  int _generation = 0;
  Timer? _retryTimer;
  @override
  String? lastError;
  @override
  StatusInfo? lastStatus;

  TransportManager({
    required this.pairing,
    required this.resolveMdns,
    required this.makeWs,
    required this.findBle,
    required this.makeBle,
  });

  Transport? get active => _active;

  /// IP the current Wi-Fi link uses (mDNS result or saved IP); null on BLE or when offline.
  String? get activeHost => _active?.kind == TransportKind.wifi ? _activeHost : null;
  @override
  TransportKind? get kind => _active?.kind;
  @override
  bool get connecting => _connecting;
  Stream<Event> get events => _events.stream;

  Future<void> start() async {
    if (_started) return;
    _started = true;
    _attempt = 0;
    _connectRound();
  }

  Future<void> stop() async {
    _started = false;
    _generation++;
    _retryTimer?.cancel();
    _detach();
  }

  @override
  void send(Map<String, dynamic> msg) {
    final t = _active;
    if (t == null) {
      lastError = 'not connected';
      notifyListeners();
      return;
    }
    t.send(msg);
  }

  // ---- internals ----

  void _connectRound() {
    if (!_started) return;
    final gen = ++_generation;
    _connecting = true;
    notifyListeners();

    var settled = false;
    Transport? bleWinner;
    Timer? graceTimer;

    void adopt(Transport t) {
      _attach(t);
      _attempt = 0;
      _connecting = false;
      lastError = null;
      notifyListeners();
    }

    final wsFuture = _tryWifi(gen);
    final bleFuture = _tryBle(gen);

    wsFuture.then((t) {
      if (gen != _generation || t == null) return;
      if (settled && bleWinner == null) {
        t.close(); // another ws already won (cannot happen, but be safe)
        return;
      }
      if (bleWinner != null) {
        if (graceTimer?.isActive == true) {
          graceTimer!.cancel();
          bleWinner!.close();
          bleWinner = null;
          adopt(t);
        } else {
          t.close();
        }
        return;
      }
      settled = true;
      adopt(t);
    });

    bleFuture.then((t) {
      if (gen != _generation || t == null) return;
      if (settled) {
        t.close();
        return;
      }
      settled = true;
      bleWinner = t;
      adopt(t);
      graceTimer = Timer(wifiGrace, () => bleWinner = null);
    });

    Future.wait([wsFuture, bleFuture]).then((results) {
      if (gen != _generation) return;
      if (results.every((r) => r == null)) {
        _connecting = false;
        lastError ??= 'projector not reachable';
        notifyListeners();
        _scheduleRetry();
      }
    });
  }

  Future<Transport?> _tryWifi(int gen) async {
    String? host;
    try {
      host = await resolveMdns(pairing.name).timeout(mdnsTimeout);
    } catch (_) {
      host = null;
    }
    host ??= pairing.ip.isEmpty ? null : pairing.ip;
    if (host == null || gen != _generation) return null;
    final t = makeWs(host);
    try {
      await t.connect();
      if (gen != _generation) {
        await t.close();
        return null;
      }
      _activeHost = host;
      return t;
    } catch (e) {
      lastError = 'wifi: $e';
      await t.close();
      return null;
    }
  }

  Future<Transport?> _tryBle(int gen) async {
    String? id;
    try {
      id = await findBle();
    } catch (_) {
      id = null;
    }
    if (id == null || gen != _generation) return null;
    final t = makeBle(id);
    try {
      await t.connect();
      if (gen != _generation) {
        await t.close();
        return null;
      }
      return t;
    } catch (e) {
      lastError = 'ble: $e';
      await t.close();
      return null;
    }
  }

  void _attach(Transport t) {
    _eventSub?.cancel();
    _stateSub?.cancel();
    _active = t;
    _eventSub = t.events.listen((e) {
      if (e is StatusEvent) {
        lastStatus = e.info;
        notifyListeners();
      }
      _events.add(e);
    });
    t.send(status());
    _stateSub = t.connected.listen((up) {
      if (!up && _active == t) _onDropped();
    });
  }

  /// Synchronous on purpose: it runs inside the transport's own `connected` callback,
  /// where awaiting the subscription cancel would never complete.
  void _detach() {
    final t = _active;
    _active = null;
    _eventSub?.cancel();
    _stateSub?.cancel();
    _eventSub = null;
    _stateSub = null;
    t?.close();
  }

  void _onDropped() {
    _detach();
    lastError = 'connection lost';
    notifyListeners();
    _scheduleRetry();
  }

  void _scheduleRetry() {
    if (!_started) return;
    final delay = backoff[_attempt.clamp(0, backoff.length - 1)];
    _attempt++;
    _retryTimer?.cancel();
    _retryTimer = Timer(delay, _connectRound);
  }
}
