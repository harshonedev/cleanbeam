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

  /// On Bluetooth the manager keeps probing Wi-Fi this often and moves over as soon as it answers.
  static const wifiRetryWhileOnBle = Duration(seconds: 15);
  /// Capped at 20 s: Android silently ignores the 6th BLE scan an app starts within 30 s, and every
  /// round starts one, so a faster retry loop would leave the phone blind to a projector that just came back.
  static const backoff = [Duration(seconds: 1), Duration(seconds: 2), Duration(seconds: 4), Duration(seconds: 8), Duration(seconds: 20)];

  /// After the app resumes, the link must answer a ping within this long or it is treated as dead.
  static const resumeProbe = Duration(seconds: 2);

  /// Called when the projector reports an IP that differs from what we last used; the app persists it.
  final void Function(String ip)? onIpLearned;

  /// Last IP the projector told us about (auth_ok / status), tried before the one from pairing.
  String? learnedIp;

  Transport? _active;
  String? _activeHost;
  Timer? _upgradeTimer;
  bool _upgrading = false;
  StreamSubscription? _eventSub;
  StreamSubscription? _stateSub;
  final _events = StreamController<Event>.broadcast();
  bool _started = false;
  bool _connecting = false;
  int _attempt = 0;
  int _generation = 0;
  Timer? _retryTimer;
  Timer? _liveness;

  /// Completes when every attempt of the latest round has settled. The next round waits for it:
  /// a stale BLE attempt that is still connecting would otherwise disconnect the very device the
  /// new round just connected (flutter_blue_plus shares one device object per remote id).
  Future<void> _inflight = Future.value();
  @override
  String? lastError;
  @override
  StatusInfo? lastStatus;
  @override
  bool authFailed = false;

  TransportManager({
    required this.pairing,
    required this.resolveMdns,
    required this.makeWs,
    required this.findBle,
    required this.makeBle,
    this.onIpLearned,
    this.learnedIp,
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
    authFailed = false;
    _connectRound();
  }

  /// Called when the app comes back to the foreground. A socket or GATT link that died while the
  /// phone slept looks open until something is sent, so probe it now instead of feeding keys into
  /// a dead link for the next 25 s; with no link up, retry at once rather than after the backoff.
  void resumed() {
    final t = _active;
    if (t == null) {
      if (_started && !_connecting && _retryTimer?.isActive == true) {
        _retryTimer!.cancel();
        _connectRound();
      }
      return;
    }
    if (_liveness?.isActive == true) return;
    t.send(ping());
    _liveness = Timer(resumeProbe, () {
      if (_active == t) {
        debugPrint('[remote] no answer after resume; dropping ${t.kind.name}');
        _onDropped();
      }
    });
  }

  Future<void> stop() async {
    _started = false;
    _generation++;
    _retryTimer?.cancel();
    _upgradeTimer?.cancel();
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
    _inflight.then((_) {
      if (gen != _generation || !_started) return;
      _startRound(gen);
    });
  }

  void _startRound(int gen) {
    var settled = false;
    Transport? bleWinner;
    Timer? graceTimer;

    void adopt(Transport t) {
      debugPrint('[remote] connected over ${t.kind.name}');
      _attach(t);
      _attempt = 0;
      _connecting = false;
      lastError = null;
      notifyListeners();
      if (t.kind == TransportKind.ble) _scheduleUpgrade(gen);
    }

    final wsFuture = _tryWifi(gen);
    final bleFuture = _tryBle(gen);
    _inflight = Future.wait([wsFuture, bleFuture]).then((_) {}, onError: (_) {});

    wsFuture.then((t) {
      if (gen != _generation || t == null) return;
      if (_active?.kind == TransportKind.wifi) {
        t.close(); // the learned-ip upgrade already moved us to Wi-Fi while this attempt was running
        return;
      }
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

  /// While on Bluetooth, probe Wi-Fi periodically (the projector's IP may have changed, or the
  /// phone's Wi-Fi may have come back) and switch when it answers.
  void _scheduleUpgrade(int gen, {Duration delay = wifiRetryWhileOnBle}) {
    _upgradeTimer?.cancel();
    _upgradeTimer = Timer(delay, () async {
      if (gen != _generation || _active?.kind != TransportKind.ble || _upgrading) return;
      _upgrading = true;
      try {
        final t = await _tryWifi(gen);
        if (t == null) return;
        if (gen != _generation || _active?.kind != TransportKind.ble) {
          await t.close();
          return;
        }
        debugPrint('[remote] wifi answered; leaving bluetooth');
        final old = _active;
        _attach(t);
        old?.close();
        lastError = null;
        notifyListeners();
      } finally {
        _upgrading = false;
        if (gen == _generation && _active?.kind == TransportKind.ble) _scheduleUpgrade(gen);
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
    host ??= learnedIp;
    host ??= pairing.ip.isEmpty ? null : pairing.ip;
    debugPrint('[remote] wifi: trying ${host ?? "(no host)"}');
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
      debugPrint('[remote] wifi failed: $e');
      _failed('wifi', e);
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
    debugPrint('[remote] ble: scan result ${id ?? "(none)"}');
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
      debugPrint('[remote] ble failed: $e');
      _failed('ble', e);
      await t.close();
      return null;
    }
  }

  void _failed(String link, Object e) {
    if (e is AuthFailed) {
      authFailed = true;
      lastError = 'The projector no longer accepts this pairing';
    } else if (!authFailed) {
      lastError = '$link: $e';
    }
  }

  void _attach(Transport t) {
    _eventSub?.cancel();
    _stateSub?.cancel();
    _active = t;
    _eventSub = t.events.listen((e) {
      _liveness?.cancel();
      if (e is StatusEvent) {
        lastStatus = e.info;
        _learnIp(e.info.ip);
        notifyListeners();
      } else if (e is AuthOk) {
        _learnIp(e.ip);
      }
      _events.add(e);
    });
    _learnIp(t.projectorIp);
    t.send(status());
    _stateSub = t.connected.listen((up) {
      if (!up && _active == t) _onDropped();
    });
  }

  void _learnIp(String? ip) {
    if (ip == null || ip.isEmpty || ip == learnedIp) return;
    debugPrint('[remote] projector ip is $ip');
    learnedIp = ip;
    onIpLearned?.call(ip);
    // A fresh address while on Bluetooth is the usual reason Wi-Fi just failed: try it right away.
    if (_active?.kind == TransportKind.ble && !_upgrading) _scheduleUpgrade(_generation, delay: Duration.zero);
  }

  /// Synchronous on purpose: it runs inside the transport's own `connected` callback,
  /// where awaiting the subscription cancel would never complete.
  void _detach() {
    _liveness?.cancel();
    final t = _active;
    _active = null;
    _eventSub?.cancel();
    _stateSub?.cancel();
    _eventSub = null;
    _stateSub = null;
    t?.close();
  }

  void _onDropped() {
    debugPrint('[remote] connection lost');
    _detach();
    lastError = 'connection lost';
    notifyListeners();
    _scheduleRetry();
  }

  void _scheduleRetry() {
    if (!_started || authFailed) return; // a rejected token will not start working by itself
    final delay = backoff[_attempt.clamp(0, backoff.length - 1)];
    _attempt++;
    _retryTimer?.cancel();
    _retryTimer = Timer(delay, _connectRound);
  }
}
