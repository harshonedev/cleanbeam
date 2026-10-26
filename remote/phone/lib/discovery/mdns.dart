import 'dart:async';

import 'package:nsd/nsd.dart' as nsd;

const serviceType = '_cb-remote._tcp';

/// Resolves the projector's current IP by Bonjour name; null when not found in time.
Future<String?> resolveProjector(String name, {Duration timeout = const Duration(seconds: 2)}) async {
  nsd.Discovery? discovery;
  try {
    discovery = await nsd.startDiscovery(serviceType, autoResolve: true);
    final completer = Completer<String?>();
    void check() {
      for (final s in discovery!.services) {
        if (s.name == name && s.host != null) {
          final host = s.host!;
          final ip = s.addresses?.map((a) => a.address).where((a) => a.contains('.')).firstOrNull;
          if (!completer.isCompleted) completer.complete(ip ?? host.replaceAll(RegExp(r'\.$'), ''));
        }
      }
    }

    discovery.addListener(check);
    check();
    return await completer.future.timeout(timeout, onTimeout: () => null);
  } catch (_) {
    return null;
  } finally {
    if (discovery != null) {
      try {
        await nsd.stopDiscovery(discovery);
      } catch (_) {}
    }
  }
}

/// Lists every projector on the network for manual pairing: name → ip.
Future<Map<String, String>> listProjectors({Duration wait = const Duration(seconds: 3)}) async {
  final out = <String, String>{};
  nsd.Discovery? discovery;
  try {
    discovery = await nsd.startDiscovery(serviceType, autoResolve: true);
    await Future.delayed(wait);
    for (final s in discovery.services) {
      final ip = s.addresses?.map((a) => a.address).where((a) => a.contains('.')).firstOrNull;
      if (s.name != null && ip != null) out[s.name!] = ip;
    }
  } catch (_) {
  } finally {
    if (discovery != null) {
      try {
        await nsd.stopDiscovery(discovery);
      } catch (_) {}
    }
  }
  return out;
}
