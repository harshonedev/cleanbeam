import 'package:flutter/foundation.dart';

import '../protocol/message.dart';
import 'transport.dart';

/// What the screens need from the connection: send commands, show state.
abstract class CommandSink implements Listenable {
  void send(Map<String, dynamic> msg);
  TransportKind? get kind;
  bool get connecting;
  String? get lastError;
  StatusInfo? get lastStatus;

  /// The projector rejected our token (it was re-paired). Retrying is pointless; the user must pair again.
  bool get authFailed;
}
