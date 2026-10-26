import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import '../protocol/chunker.dart';
import '../protocol/message.dart';
import 'ble_gatt.dart';
import 'transport.dart';

/// BLE transport: JSON over the cmd/evt characteristics, chunked per PROTOCOL.md.
class BleTransport implements Transport {
  final BleGatt gatt;
  final String? token;
  final String? pairCode;
  final Duration timeout;

  int _mtu = 23;
  final _assembler = ChunkAssembler();
  final _events = StreamController<Event>.broadcast();
  final _connected = StreamController<bool>.broadcast();
  StreamSubscription? _notifySub;
  StreamSubscription? _dropSub;
  bool _isConnected = false;
  Future<void> _writeQueue = Future.value();
  String? pairedToken;
  String? pairedName;

  @override
  String? projectorIp;

  BleTransport(this.gatt, {this.token, this.pairCode, this.timeout = const Duration(seconds: 10)})
      : assert(token != null || pairCode != null);

  @override
  TransportKind get kind => TransportKind.ble;

  @override
  Stream<Event> get events => _events.stream;

  @override
  Stream<bool> get connected => _connected.stream;

  bool get isConnected => _isConnected;

  @override
  Future<void> connect() async {
    final authDone = Completer<void>();
    await gatt.connect().timeout(timeout);
    _mtu = await gatt.requestMtu(512);
    _notifySub = gatt.evtNotifications.listen((chunk) {
      final msg = _assembler.feed(chunk);
      if (msg == null) return;
      final event = parseEvent(utf8.decode(msg));
      if (!authDone.isCompleted) {
        switch (event) {
          case AuthOk(:final ip):
            projectorIp = ip;
            _isConnected = true;
            _connected.add(true);
            authDone.complete();
          case PairOk(:final token, :final name):
            pairedToken = token;
            pairedName = name;
            _isConnected = true;
            _connected.add(true);
            authDone.complete();
            _events.add(AuthOk(name, ''));
          case AuthFail():
            authDone.completeError(const AuthFailed('auth_fail'));
          case PairFail():
            authDone.completeError(const AuthFailed('pair_fail'));
          default:
            break;
        }
        return;
      }
      _events.add(event);
    });
    _dropSub = gatt.disconnected.listen((_) => _onDropped());
    send(pairCode != null ? pair(pairCode!) : auth(token!));
    try {
      await authDone.future.timeout(timeout);
    } catch (_) {
      await close();
      rethrow;
    }
  }

  void _onDropped() {
    if (_isConnected) {
      _isConnected = false;
      if (!_connected.isClosed) _connected.add(false);
    }
  }

  @override
  void send(Map<String, dynamic> msg) {
    final bytes = Uint8List.fromList(utf8.encode(jsonEncode(msg)));
    final chunks = splitChunks(bytes, _mtu);
    // Serialise writes so chunks of consecutive messages never interleave.
    _writeQueue = _writeQueue.then((_) async {
      for (final c in chunks) {
        await gatt.writeCmd(c);
      }
    }).catchError((_) {});
  }

  @override
  Future<void> close() async {
    await _notifySub?.cancel();
    await _dropSub?.cancel();
    await gatt.disconnect();
    _onDropped();
  }
}
