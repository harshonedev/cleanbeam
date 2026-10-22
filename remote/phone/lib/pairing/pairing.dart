import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

/// What the phone remembers about its projector.
class Pairing {
  final String name;
  final String ip;
  final int port;
  final String token;

  const Pairing({required this.name, required this.ip, required this.port, required this.token});

  /// Parses the QR payload shown on the projector; null when it is not ours.
  static Pairing? fromQrJson(String raw) {
    final dynamic d;
    try {
      d = jsonDecode(raw);
    } catch (_) {
      return null;
    }
    if (d is! Map || d['v'] != 1) return null;
    final token = d['token']?.toString();
    if (token == null || token.isEmpty) return null;
    return Pairing(
      name: d['name']?.toString() ?? 'EGate',
      ip: d['ip']?.toString() ?? '',
      port: (d['port'] as num?)?.toInt() ?? 7272,
      token: token,
    );
  }

  Map<String, dynamic> toJson() => {'name': name, 'ip': ip, 'port': port, 'token': token};

  factory Pairing.fromJson(Map<String, dynamic> j) => Pairing(
        name: j['name'] as String? ?? 'EGate',
        ip: j['ip'] as String? ?? '',
        port: (j['port'] as num?)?.toInt() ?? 7272,
        token: j['token'] as String,
      );

  Pairing copyWith({String? ip}) => Pairing(name: name, ip: ip ?? this.ip, port: port, token: token);
}

class PairingStore {
  static const _key = 'pairing';

  Future<Pairing?> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw == null) return null;
    try {
      return Pairing.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      return null;
    }
  }

  Future<void> save(Pairing p) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, jsonEncode(p.toJson()));
  }

  Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_key);
  }
}
