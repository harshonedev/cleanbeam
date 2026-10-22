import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../discovery/mdns.dart';
import '../discovery/permissions.dart';
import '../transport/ws_transport.dart';
import '../ui/theme.dart';
import 'pairing.dart';

class PairScreen extends StatefulWidget {
  final Future<void> Function(Pairing) onPaired;
  const PairScreen({super.key, required this.onPaired});

  @override
  State<PairScreen> createState() => _PairScreenState();
}

class _PairScreenState extends State<PairScreen> {
  bool _busy = false;
  String? _error;
  bool? _cameraOk;

  @override
  void initState() {
    super.initState();
    ensureCameraPermission().then((ok) {
      if (mounted) setState(() => _cameraOk = ok);
    });
  }

  Future<void> _detected(BarcodeCapture capture) async {
    if (_busy) return;
    for (final b in capture.barcodes) {
      final p = Pairing.fromQrJson(b.rawValue ?? '');
      if (p != null) {
        setState(() => _busy = true);
        await widget.onPaired(p);
        return;
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(24, 28, 24, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Pair with your projector', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w700)),
                  SizedBox(height: 8),
                  Text('Open “Remote pairing” on the projector, then point the camera at the code on screen.',
                      style: TextStyle(color: Tokens.muted, fontSize: 15, height: 1.4)),
                ],
              ),
            ),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(24),
                  child: switch (_cameraOk) {
                    true => MobileScanner(onDetect: _detected),
                    false => Container(
                        color: Tokens.surface,
                        alignment: Alignment.center,
                        padding: const EdgeInsets.all(24),
                        child: const Text('Camera access is off. Allow it in Settings, or use the code below.',
                            textAlign: TextAlign.center, style: TextStyle(color: Tokens.muted, height: 1.4)),
                      ),
                    null => Container(color: Tokens.surface),
                  },
                ),
              ),
            ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.fromLTRB(24, 12, 24, 0),
                child: Text(_error!, style: const TextStyle(color: Tokens.warn)),
              ),
            Padding(
              padding: const EdgeInsets.all(24),
              child: SizedBox(
                width: double.infinity,
                child: OutlinedButton(
                  onPressed: _busy ? null : () => _manual(context),
                  style: OutlinedButton.styleFrom(foregroundColor: Tokens.ink, side: const BorderSide(color: Tokens.raised, width: 2), padding: const EdgeInsets.symmetric(vertical: 16)),
                  child: const Text('Enter the 6-digit code instead'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _manual(BuildContext context) async {
    final result = await showModalBottomSheet<Pairing>(
      context: context,
      backgroundColor: Tokens.surface,
      isScrollControlled: true,
      builder: (ctx) => _ManualSheet(),
    );
    if (result != null) {
      setState(() => _busy = true);
      await widget.onPaired(result);
    }
  }
}

class _ManualSheet extends StatefulWidget {
  @override
  State<_ManualSheet> createState() => _ManualSheetState();
}

class _ManualSheetState extends State<_ManualSheet> {
  Map<String, String> _found = {};
  String? _selected;
  final _code = TextEditingController();
  bool _searching = true;
  bool _pairing = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    listProjectors().then((f) {
      if (!mounted) return;
      setState(() {
        _found = f;
        _searching = false;
        if (f.length == 1) _selected = f.keys.first;
      });
    });
  }

  Future<void> _pair() async {
    final name = _selected;
    final ip = name == null ? null : _found[name];
    if (ip == null) {
      setState(() => _error = 'Pick a projector first.');
      return;
    }
    setState(() {
      _pairing = true;
      _error = null;
    });
    final t = WsTransport(ip, 7272, pairCode: _code.text.trim());
    try {
      await t.connect();
      final token = t.pairedToken;
      await t.close();
      if (token == null) throw Exception('no token');
      if (mounted) Navigator.of(context).pop(Pairing(name: name!, ip: ip, port: 7272, token: token));
    } catch (e) {
      await t.close();
      setState(() {
        _pairing = false;
        _error = 'That code was not accepted. Reopen “Remote pairing” on the projector for a fresh code.';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(24, 20, 24, 24 + MediaQuery.viewInsetsOf(context).bottom),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text('Pair with a code', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700)),
          const SizedBox(height: 16),
          if (_searching)
            const Row(children: [SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)), SizedBox(width: 12), Text('Looking for projectors on this Wi-Fi…')])
          else if (_found.isEmpty)
            const Text('No projector found on this Wi-Fi. Both devices must be on the same network for code pairing.', style: TextStyle(color: Tokens.muted))
          else
            Wrap(
              spacing: 8,
              children: [
                for (final n in _found.keys)
                  ChoiceChip(label: Text(n), selected: _selected == n, onSelected: (_) => setState(() => _selected = n)),
              ],
            ),
          const SizedBox(height: 16),
          TextField(
            controller: _code,
            keyboardType: TextInputType.number,
            maxLength: 6,
            style: const TextStyle(fontSize: 24, letterSpacing: 6),
            decoration: const InputDecoration(labelText: '6-digit code', counterText: ''),
          ),
          if (_error != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_error!, style: const TextStyle(color: Tokens.warn))),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              onPressed: _pairing ? null : _pair,
              style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 16)),
              child: Text(_pairing ? 'Pairing…' : 'Pair'),
            ),
          ),
        ],
      ),
    );
  }
}
