import 'dart:io';

import 'package:permission_handler/permission_handler.dart';

/// Asks for what BLE scanning needs on this OS. Returns true when scanning is allowed.
Future<bool> ensureBlePermissions() async {
  if (Platform.isAndroid) {
    final wanted = <Permission>[Permission.bluetoothScan, Permission.bluetoothConnect, Permission.locationWhenInUse];
    final results = await wanted.request();
    final scan = results[Permission.bluetoothScan];
    final connect = results[Permission.bluetoothConnect];
    // Android 12+ grants the two bluetooth permissions; older versions report them as granted and need location.
    return (scan?.isGranted ?? false) && (connect?.isGranted ?? false) || (results[Permission.locationWhenInUse]?.isGranted ?? false);
  }
  if (Platform.isIOS) {
    return (await Permission.bluetooth.request()).isGranted;
  }
  return true;
}

Future<bool> ensureCameraPermission() async => (await Permission.camera.request()).isGranted;
