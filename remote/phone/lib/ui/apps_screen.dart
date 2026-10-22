import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../apps/favorites.dart';
import '../protocol/message.dart' as msg;
import '../protocol/message.dart' show AppItem;
import '../transport/command_sink.dart';
import 'theme.dart';

class AppsScreen extends StatefulWidget {
  final CommandSink sink;
  final List<AppItem> apps;
  final Favorites favorites;

  /// `http://ip:7273` when icons are reachable (Wi-Fi), null for letter tiles only.
  final String? iconBase;
  final Future<void> Function()? onRefresh;

  const AppsScreen({super.key, required this.sink, required this.apps, required this.favorites, required this.iconBase, this.onRefresh});

  @override
  State<AppsScreen> createState() => _AppsScreenState();
}

class _AppsScreenState extends State<AppsScreen> {
  Set<String> _fav = {};

  @override
  void initState() {
    super.initState();
    widget.favorites.load().then((f) {
      if (mounted) setState(() => _fav = f);
    });
  }

  Future<void> _toggle(String pkg) async {
    HapticFeedback.mediumImpact();
    setState(() => _fav.contains(pkg) ? _fav.remove(pkg) : _fav.add(pkg));
    await widget.favorites.save(_fav);
  }

  @override
  Widget build(BuildContext context) {
    final sorted = [...widget.apps]..sort((a, b) {
        final fa = _fav.contains(a.pkg), fb = _fav.contains(b.pkg);
        if (fa != fb) return fa ? -1 : 1;
        return a.label.toLowerCase().compareTo(b.label.toLowerCase());
      });
    if (sorted.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(32),
          child: Text('No apps yet. Connect to the projector and pull down to refresh.',
              textAlign: TextAlign.center, style: TextStyle(color: Tokens.muted)),
        ),
      );
    }
    final grid = GridView.builder(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
      gridDelegate: const SliverGridDelegateWithMaxCrossAxisExtent(maxCrossAxisExtent: 120, mainAxisSpacing: 12, crossAxisSpacing: 12, childAspectRatio: 0.82),
      itemCount: sorted.length,
      itemBuilder: (_, i) => _tile(sorted[i]),
    );
    return widget.onRefresh == null ? grid : RefreshIndicator(onRefresh: widget.onRefresh!, child: grid);
  }

  Widget _tile(AppItem a) {
    final fav = _fav.contains(a.pkg);
    final base = widget.iconBase;
    return GestureDetector(
      onTap: () {
        HapticFeedback.selectionClick();
        widget.sink.send(msg.launch(a.pkg));
      },
      onLongPress: () => _toggle(a.pkg),
      child: Column(
        children: [
          Stack(
            children: [
              Container(
                width: 64,
                height: 64,
                decoration: BoxDecoration(color: Tokens.raised, borderRadius: BorderRadius.circular(18)),
                clipBehavior: Clip.antiAlias,
                child: base == null
                    ? _letter(a.label)
                    : CachedNetworkImage(
                        imageUrl: '$base/icon/${a.pkg}',
                        fit: BoxFit.cover,
                        errorWidget: (_, _, _) => _letter(a.label),
                        placeholder: (_, _) => _letter(a.label),
                      ),
              ),
              if (fav)
                const Positioned(right: 2, top: 2, child: Icon(Icons.star_rounded, size: 16, color: Tokens.warn)),
            ],
          ),
          const SizedBox(height: 8),
          Text(a.label, maxLines: 2, textAlign: TextAlign.center, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12.5)),
        ],
      ),
    );
  }

  Widget _letter(String label) => Center(
        child: Text(label.isEmpty ? '?' : label[0].toUpperCase(),
            style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w700, color: Tokens.accent)),
      );
}
