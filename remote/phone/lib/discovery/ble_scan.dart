import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

import '../transport/ble_gatt.dart';
import 'permissions.dart';

/// Finds the projector by its service UUID; returns the remoteId or null.
Future<String?> findProjectorBle({Duration timeout = const Duration(seconds: 5)}) async {
  try {
    if (await FlutterBluePlus.isSupported == false) return null;
    if (!await ensureBlePermissions()) return null;
    if (FlutterBluePlus.adapterStateNow != BluetoothAdapterState.on) {
      // iOS reports "unknown" until CoreBluetooth has started (and the user has answered its prompt).
      await FlutterBluePlus.adapterState
          .where((s) => s == BluetoothAdapterState.on)
          .first
          .timeout(const Duration(seconds: 6));
    }
    final completer = Completer<String?>();
    final sub = FlutterBluePlus.onScanResults.listen((results) {
      for (final r in results) {
        if (!completer.isCompleted) completer.complete(r.device.remoteId.str);
      }
    });
    await FlutterBluePlus.startScan(withServices: [Guid(bleServiceUuid)], timeout: timeout);
    final id = await completer.future.timeout(timeout, onTimeout: () => null);
    await sub.cancel();
    await FlutterBluePlus.stopScan();
    return id;
  } catch (e) {
    debugPrint('[egate] ble scan error: $e (adapter ${FlutterBluePlus.adapterStateNow})');
    return null;
  }
}

BluetoothDevice deviceFromId(String id) => BluetoothDevice.fromId(id);
