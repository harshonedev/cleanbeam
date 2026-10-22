import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:web_socket_channel/io.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../protocol/message.dart';
import 'transport.dart';

/// Wi-Fi transport: WebSocket to ws://host:port/, auth (or pair) as the first message.
class WsTransport implements Transport {
  final String host;
  final int port;
  final String? token;
  final String? pairCode;
  final Duration timeout;
  final Duration pingInterval;

  WebSocketChannel? _channel;
  final _events = StreamController<Event>.broadcast();
  final _connected = StreamController<bool>.broadcast();
  Timer? _pingTimer;
  bool _isConnected = false;
  bool _closed = false;

  /// Set after a successful `pair` handshake.
  String? pairedToken;

  WsTransport(
    this.host,
    this.port, {
    this.token,
    this.pairCode,
    this.timeout = const Duration(seconds: 3),
    this.pingInterval = const Duration(seconds: 10),
  }) : assert(token != null || pairCode != null);

  @override
  TransportKind get kind => TransportKind.wifi;

  @override
  Stream<Event> get events => _events.stream;

  @override
  Stream<bool> get connected => _connected.stream;

  bool get isConnected => _isConnected;

  @override
  Future<void> connect() async {
    final authDone = Completer<void>();
    final uri = Uri(scheme: 'ws', host: host, port: port, path: '/');
    final WebSocket socket;
    try {
      socket = await WebSocket.connect(uri.toString()).timeout(timeout);
    } on TimeoutException {
      rethrow;
    } on SocketException catch (e) {
      throw TimeoutException('connect failed: ${e.message}');
    }
    final channel = IOWebSocketChannel(socket);
    _channel = channel;

    channel.stream.listen((data) {
      final event = parseEvent(data as String);
      if (!authDone.isCompleted) {
        switch (event) {
          case AuthOk():
            _onAuthenticated();
            authDone.complete();
          case PairOk(:final token, :final name):
            pairedToken = token;
            _onAuthenticated();
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
    }, onDone: _onDropped, onError: (_) => _onDropped());

    channel.sink.add(jsonEncode(pairCode != null ? pair(pairCode!) : auth(token!)));
    try {
      await authDone.future.timeout(timeout);
    } catch (_) {
      await close();
      rethrow;
    }
  }

  void _onAuthenticated() {
    _isConnected = true;
    _connected.add(true);
    _pingTimer = Timer.periodic(pingInterval, (_) => send(ping()));
  }

  void _onDropped() {
    _pingTimer?.cancel();
    if (_isConnected) {
      _isConnected = false;
      if (!_connected.isClosed) _connected.add(false);
    }
  }

  @override
  void send(Map<String, dynamic> msg) {
    final c = _channel;
    if (c == null || _closed) return;
    c.sink.add(jsonEncode(msg));
  }

  @override
  Future<void> close() async {
    _closed = true;
    _pingTimer?.cancel();
    final c = _channel;
    _channel = null;
    if (c != null) {
      try {
        await c.sink.close();
      } catch (_) {}
    }
    _onDropped();
  }
}
