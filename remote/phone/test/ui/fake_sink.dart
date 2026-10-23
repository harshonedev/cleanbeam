import 'package:flutter/foundation.dart';
import 'package:remote_app/protocol/message.dart';
import 'package:remote_app/transport/command_sink.dart';
import 'package:remote_app/transport/transport.dart';

class FakeSink extends ChangeNotifier implements CommandSink {
  final sent = <Map<String, dynamic>>[];
  @override
  TransportKind? kind = TransportKind.wifi;
  @override
  bool connecting = false;
  @override
  String? lastError;
  @override
  StatusInfo? lastStatus;
  @override
  bool authFailed = false;
  @override
  void send(Map<String, dynamic> msg) => sent.add(msg);
}
