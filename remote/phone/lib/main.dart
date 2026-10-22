import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'discovery/ble_scan.dart';
import 'discovery/mdns.dart';
import 'pairing/pair_screen.dart';
import 'pairing/pairing.dart';
import 'transport/ble_gatt.dart';
import 'transport/ble_transport.dart';
import 'transport/transport_manager.dart';
import 'transport/ws_transport.dart';
import 'ui/app_shell.dart';
import 'ui/theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([DeviceOrientation.portraitUp]);
  runApp(const EGateRemoteApp());
}

class EGateRemoteApp extends StatefulWidget {
  const EGateRemoteApp({super.key});

  @override
  State<EGateRemoteApp> createState() => _EGateRemoteAppState();
}

class _EGateRemoteAppState extends State<EGateRemoteApp> {
  final _store = PairingStore();
  Pairing? _pairing;
  bool _loaded = false;
  TransportManager? _manager;

  @override
  void initState() {
    super.initState();
    _store.load().then((p) => setState(() {
          _pairing = p;
          _loaded = true;
          if (p != null) _manager = _makeManager(p);
        }));
  }

  TransportManager _makeManager(Pairing p) => TransportManager(
        pairing: p,
        resolveMdns: (name) => resolveProjector(name),
        makeWs: (host) => WsTransport(host, p.port, token: p.token),
        findBle: findProjectorBle,
        makeBle: (id) => BleTransport(FlutterBluePlusGatt(deviceFromId(id)), token: p.token),
      );

  Future<void> _onPaired(Pairing p) async {
    await _store.save(p);
    setState(() {
      _pairing = p;
      _manager = _makeManager(p);
    });
  }

  Future<void> _onUnpair() async {
    await _manager?.stop();
    await _store.clear();
    setState(() {
      _pairing = null;
      _manager = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'EGate Remote',
      theme: buildTheme(),
      debugShowCheckedModeBanner: false,
      home: !_loaded
          ? const Scaffold()
          : _pairing == null
              ? PairScreen(onPaired: _onPaired)
              : AppShell(key: ValueKey(_pairing!.token), pairing: _pairing!, manager: _manager!, onUnpair: _onUnpair),
    );
  }
}
