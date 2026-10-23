/// JSON message helpers. Mirrors remote/PROTOCOL.md and the receiver's Message.kt.
library;

import 'dart:convert';

// ---- phone → projector ----

Map<String, dynamic> auth(String token) => {'t': 'auth', 'token': token};
Map<String, dynamic> pair(String code) => {'t': 'pair', 'code': code};
Map<String, dynamic> key(int code, {String action = 'tap'}) => {'t': 'key', 'code': code, 'action': action};
Map<String, dynamic> text(String s) => {'t': 'text', 's': s};
Map<String, dynamic> mouse(int dx, int dy) => {'t': 'mouse', 'dx': dx, 'dy': dy};
Map<String, dynamic> click(String button) => {'t': 'click', 'button': button};
Map<String, dynamic> scroll(int dy) => {'t': 'scroll', 'dy': dy};
Map<String, dynamic> launch(String pkg) => {'t': 'launch', 'pkg': pkg};
Map<String, dynamic> apps() => {'t': 'apps'};
Map<String, dynamic> power(String action) => {'t': 'power', 'action': action};
Map<String, dynamic> status() => {'t': 'status'};
Map<String, dynamic> ping() => {'t': 'ping'};
Map<String, dynamic> shortcuts() => {'t': 'shortcuts'};
Map<String, dynamic> open(String id) => {'t': 'open', 'id': id};

// ---- projector → phone ----

class AppItem {
  final String pkg;
  final String label;
  const AppItem(this.pkg, this.label);
}

/// A projector/system screen the receiver can open on request (`open`).
class ShortcutItem {
  final String id;
  final String label;
  final String group;
  const ShortcutItem(this.id, this.label, this.group);
}

class StatusInfo {
  final String? app;
  final bool screenOn;
  final int volume;
  final int maxVolume;

  /// The projector's current Wi-Fi IPv4, when the receiver reports it.
  final String? ip;
  const StatusInfo({this.app, required this.screenOn, required this.volume, required this.maxVolume, this.ip});
}

sealed class Event {
  const Event();
}

class AuthOk extends Event {
  final String name;
  final String version;
  final String? ip;
  const AuthOk(this.name, this.version, {this.ip});
}

class AuthFail extends Event {
  const AuthFail();
}

class PairOk extends Event {
  final String token;
  final String name;
  const PairOk(this.token, this.name);
}

class PairFail extends Event {
  const PairFail();
}

class AppsEvent extends Event {
  final List<AppItem> items;
  const AppsEvent(this.items);
}

class StatusEvent extends Event {
  final StatusInfo info;
  const StatusEvent(this.info);
}

class ShortcutsEvent extends Event {
  final List<ShortcutItem> items;
  const ShortcutsEvent(this.items);
}

class Pong extends Event {
  const Pong();
}

class ErrEvent extends Event {
  final String msg;
  const ErrEvent(this.msg);
}

class UnknownEvent extends Event {
  final String t;
  const UnknownEvent(this.t);
}

Event parseEvent(String json) {
  final dynamic decoded;
  try {
    decoded = jsonDecode(json);
  } catch (_) {
    return const UnknownEvent('');
  }
  if (decoded is! Map) return const UnknownEvent('');
  final o = decoded;
  final t = o['t'];
  switch (t) {
    case 'auth_ok':
      return AuthOk(o['name']?.toString() ?? '', o['version']?.toString() ?? '', ip: o['ip']?.toString());
    case 'auth_fail':
      return const AuthFail();
    case 'pair_ok':
      return PairOk(o['token']?.toString() ?? '', o['name']?.toString() ?? '');
    case 'pair_fail':
      return const PairFail();
    case 'apps':
      final items = (o['items'] as List? ?? const [])
          .whereType<Map>()
          .map((m) => AppItem(m['pkg']?.toString() ?? '', m['label']?.toString() ?? ''))
          .toList();
      return AppsEvent(items);
    case 'status':
      return StatusEvent(StatusInfo(
        app: o['app']?.toString(),
        screenOn: o['screenOn'] == true,
        volume: (o['volume'] as num?)?.toInt() ?? 0,
        maxVolume: (o['maxVolume'] as num?)?.toInt() ?? 0,
        ip: o['ip']?.toString(),
      ));
    case 'shortcuts':
      final items = (o['items'] as List? ?? const [])
          .whereType<Map>()
          .map((m) => ShortcutItem(m['id']?.toString() ?? '', m['label']?.toString() ?? '', m['group']?.toString() ?? ''))
          .where((i) => i.id.isNotEmpty)
          .toList();
      return ShortcutsEvent(items);
    case 'pong':
      return const Pong();
    case 'err':
      return ErrEvent(o['msg']?.toString() ?? '');
    default:
      return UnknownEvent(t?.toString() ?? '');
  }
}
