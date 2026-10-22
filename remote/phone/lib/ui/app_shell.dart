import 'dart:async';

import 'package:flutter/material.dart';

import '../apps/favorites.dart';
import '../pairing/pairing.dart';
import '../protocol/message.dart' as msg;
import '../protocol/message.dart' show AppItem, AppsEvent, Event;
import '../transport/transport.dart';
import '../transport/transport_manager.dart';
import 'apps_screen.dart';
import 'remote_screen.dart';
import 'theme.dart';
import 'touchpad_screen.dart';

class AppShell extends StatefulWidget {
  final Pairing pairing;
  final TransportManager manager;
  final Future<void> Function() onUnpair;
  const AppShell({super.key, required this.pairing, required this.manager, required this.onUnpair});

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  int _tab = 0;
  List<AppItem> _apps = const [];
  StreamSubscription<Event>? _sub;
  TransportKind? _lastKind;

  @override
  void initState() {
    super.initState();
    _sub = widget.manager.events.listen((e) {
      if (e is AppsEvent) setState(() => _apps = e.items);
    });
    widget.manager.addListener(_onManager);
    widget.manager.start();
  }

  void _onManager() {
    final k = widget.manager.kind;
    if (k != null && k != _lastKind) widget.manager.send(msg.apps());
    _lastKind = k;
    final err = widget.manager.lastError;
    if (err == 'connection lost' && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Connection lost. Reconnecting…')));
    }
  }

  @override
  void dispose() {
    _sub?.cancel();
    widget.manager.removeListener(_onManager);
    widget.manager.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final m = widget.manager;
    final host = m.activeHost;
    final iconBase = m.kind == TransportKind.wifi && host != null ? 'http://$host:7273' : null;
    final pages = [
      RemoteScreen(sink: m),
      AppsScreen(
        sink: m,
        apps: _apps,
        favorites: PrefsFavorites(),
        iconBase: iconBase,
        onRefresh: () async {
          m.send(msg.apps());
          await Future.delayed(const Duration(milliseconds: 600));
        },
      ),
      TouchpadScreen(sink: m),
    ];
    return Scaffold(
      appBar: AppBar(
        title: Text(widget.pairing.name, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
        actions: [
          PopupMenuButton<String>(
            color: Tokens.raised,
            onSelected: (v) async {
              if (v == 'unpair') await widget.onUnpair();
              if (v == 'reconnect') {
                await m.stop();
                await m.start();
              }
            },
            itemBuilder: (_) => const [
              PopupMenuItem(value: 'reconnect', child: Text('Reconnect')),
              PopupMenuItem(value: 'unpair', child: Text('Forget projector')),
            ],
          ),
        ],
      ),
      body: SafeArea(child: IndexedStack(index: _tab, children: pages)),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.gamepad_outlined), selectedIcon: Icon(Icons.gamepad_rounded), label: 'Remote'),
          NavigationDestination(icon: Icon(Icons.apps_outlined), selectedIcon: Icon(Icons.apps_rounded), label: 'Apps'),
          NavigationDestination(icon: Icon(Icons.touch_app_outlined), selectedIcon: Icon(Icons.touch_app_rounded), label: 'Touchpad'),
        ],
      ),
    );
  }
}
