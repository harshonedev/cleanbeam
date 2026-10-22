import 'package:shared_preferences/shared_preferences.dart';

abstract class Favorites {
  Future<Set<String>> load();
  Future<void> save(Set<String> pkgs);
}

class PrefsFavorites implements Favorites {
  static const _key = 'favorites';

  @override
  Future<Set<String>> load() async {
    final p = await SharedPreferences.getInstance();
    return (p.getStringList(_key) ?? const []).toSet();
  }

  @override
  Future<void> save(Set<String> pkgs) async {
    final p = await SharedPreferences.getInstance();
    await p.setStringList(_key, pkgs.toList()..sort());
  }
}
