import 'dart:async';
import 'dart:typed_data';

import 'package:flutter_blue_plus/flutter_blue_plus.dart';

/// UUIDs from remote/PROTOCOL.md.
const bleServiceUuid = 'b6517522-6d7b-49d2-8488-664819daea01';
const bleCmdUuid = 'b6517522-6d7b-49d2-8488-664819daea02';
const bleEvtUuid = 'b6517522-6d7b-49d2-8488-664819daea03';

/// Minimal GATT link the transport needs; the real one wraps flutter_blue_plus so tests can fake it.
abstract class BleGatt {
  Future<void> connect();
  Future<int> requestMtu(int mtu);
  Future<void> writeCmd(Uint8List chunk);
  Stream<Uint8List> get evtNotifications;
  Stream<void> get disconnected;
  Future<void> disconnect();
}

class FlutterBluePlusGatt implements BleGatt {
  final BluetoothDevice device;
  BluetoothCharacteristic? _cmd;
  BluetoothCharacteristic? _evt;
  final _notifications = StreamController<Uint8List>.broadcast();
  final _disconnected = StreamController<void>.broadcast();
  StreamSubscription? _valueSub;
  StreamSubscription? _stateSub;

  FlutterBluePlusGatt(this.device);

  @override
  Future<void> connect() async {
    await device.connect(license: License.nonprofit, timeout: const Duration(seconds: 8), autoConnect: false);
    _stateSub = device.connectionState.listen((s) {
      if (s == BluetoothConnectionState.disconnected) _disconnected.add(null);
    });
    final services = await device.discoverServices();
    final svc = services.firstWhere((s) => s.uuid.str128.toLowerCase() == bleServiceUuid);
    _cmd = svc.characteristics.firstWhere((c) => c.uuid.str128.toLowerCase() == bleCmdUuid);
    _evt = svc.characteristics.firstWhere((c) => c.uuid.str128.toLowerCase() == bleEvtUuid);
    _valueSub = _evt!.onValueReceived.listen((v) => _notifications.add(Uint8List.fromList(v)));
    await _evt!.setNotifyValue(true);
  }

  @override
  Future<int> requestMtu(int mtu) async {
    try {
      return await device.requestMtu(mtu);
    } catch (_) {
      return device.mtuNow;
    }
  }

  @override
  Future<void> writeCmd(Uint8List chunk) =>
      _cmd!.write(chunk, withoutResponse: _cmd!.properties.writeWithoutResponse);

  @override
  Stream<Uint8List> get evtNotifications => _notifications.stream;

  @override
  Stream<void> get disconnected => _disconnected.stream;

  @override
  Future<void> disconnect() async {
    await _valueSub?.cancel();
    await _stateSub?.cancel();
    try {
      await device.disconnect();
    } catch (_) {}
  }
}
