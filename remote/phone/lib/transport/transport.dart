import '../protocol/message.dart';

enum TransportKind { wifi, ble }

class AuthFailed implements Exception {
  final String reason;
  const AuthFailed([this.reason = 'auth_fail']);
  @override
  String toString() => 'AuthFailed($reason)';
}

/// One way of reaching the projector. Same JSON protocol over any implementation.
abstract class Transport {
  TransportKind get kind;

  /// Decoded events from the projector.
  Stream<Event> get events;

  /// Emits true once authenticated and false when the link drops.
  Stream<bool> get connected;

  /// Connects and authenticates. Completes on auth_ok; throws [AuthFailed] or [TimeoutException].
  Future<void> connect();

  void send(Map<String, dynamic> msg);

  Future<void> close();
}
