/// BLE framing: byte0 = (seq & 0x7F) | (last ? 0x80 : 0), then payload. See remote/PROTOCOL.md.
library;

import 'dart:typed_data';

const _last = 0x80;
const _seqMask = 0x7F;
const _attOverhead = 3;
const _header = 1;
const defaultMaxMessage = 64 * 1024;

int chunkPayloadSize(int mtu) => (mtu - _attOverhead - _header).clamp(1, 1 << 20);

List<Uint8List> splitChunks(Uint8List msg, int mtu) {
  final size = chunkPayloadSize(mtu);
  final chunks = <Uint8List>[];
  var off = 0;
  var seq = 0;
  do {
    final n = (msg.length - off) < size ? (msg.length - off) : size;
    final last = off + n >= msg.length;
    final c = Uint8List(1 + n);
    c[0] = (seq & _seqMask) | (last ? _last : 0);
    c.setRange(1, 1 + n, msg, off);
    chunks.add(c);
    off += n;
    seq = (seq + 1) & _seqMask;
  } while (off < msg.length);
  return chunks;
}

class ChunkAssembler {
  final int maxMessage;
  final BytesBuilder _buf = BytesBuilder(copy: false);
  int _expected = 0;
  bool _open = false;

  ChunkAssembler({this.maxMessage = defaultMaxMessage});

  void _reset() {
    _buf.clear();
    _open = false;
    _expected = 0;
  }

  /// Returns the complete message when [chunk] finishes one, else null.
  Uint8List? feed(Uint8List chunk) {
    if (chunk.isEmpty) return null;
    final h = chunk[0];
    final seq = h & _seqMask;
    final last = (h & _last) != 0;
    if (!_open || seq != _expected) {
      _reset();
      if (seq != 0) return null;
      _open = true;
    }
    if (_buf.length + chunk.length - 1 > maxMessage) {
      _reset();
      return null;
    }
    _buf.add(chunk.sublist(1));
    _expected = (seq + 1) & _seqMask;
    if (last) {
      final out = _buf.takeBytes();
      _reset();
      return out;
    }
    return null;
  }
}
