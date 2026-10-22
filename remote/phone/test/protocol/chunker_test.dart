import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/protocol/chunker.dart';

void main() {
  Uint8List bytes(String s) => Uint8List.fromList(utf8.encode(s));

  test('single chunk when fits', () {
    final chunks = splitChunks(bytes('{"t":"ping"}'), 23);
    expect(chunks.length, 1);
    expect(chunks[0][0], 0x80);
    expect(utf8.decode(chunks[0].sublist(1)), '{"t":"ping"}');
  });

  test('splits 1000 bytes at mtu 23 into 53 chunks', () {
    final msg = Uint8List.fromList(List.generate(1000, (i) => i % 251));
    final chunks = splitChunks(msg, 23);
    expect(chunks.length, 53);
    expect(chunks[0][0], 0x00);
    expect(chunks[1][0], 0x01);
    expect(chunks[52][0], 0x80 | 52);
    expect(chunks[0].length, 20);
    expect(chunks[52].length, 1 + (1000 - 52 * 19));
  });

  test('seq wraps at 128', () {
    final chunks = splitChunks(Uint8List(19 * 130), 23);
    expect(chunks.length, 130);
    expect(chunks[127][0], 127);
    expect(chunks[128][0], 0);
    expect(chunks[129][0], 0x80 | 1);
  });

  test('assembler rebuilds', () {
    final msg = Uint8List.fromList(List.generate(1000, (i) => i % 251));
    final a = ChunkAssembler();
    final chunks = splitChunks(msg, 23);
    for (var i = 0; i < chunks.length - 1; i++) {
      expect(a.feed(chunks[i]), isNull);
    }
    expect(a.feed(chunks.last), msg);
  });

  test('assembler discards on seq gap and restarts', () {
    final msg = Uint8List.fromList(List.generate(100, (i) => i));
    final chunks = splitChunks(msg, 23);
    final a = ChunkAssembler();
    a.feed(chunks[0]);
    expect(a.feed(chunks[2]), isNull);
    expect(a.feed(chunks[5]), isNull);
    for (var i = 0; i < chunks.length - 1; i++) {
      expect(a.feed(chunks[i]), isNull);
    }
    expect(a.feed(chunks.last), msg);
  });

  test('twenty back-to-back single messages stay ordered', () {
    final a = ChunkAssembler();
    final out = <String>[];
    for (var i = 0; i < 20; i++) {
      for (final c in splitChunks(bytes('{"t":"key","code":$i}'), 512)) {
        final m = a.feed(c);
        if (m != null) out.add(utf8.decode(m));
      }
    }
    expect(out, List.generate(20, (i) => '{"t":"key","code":$i}'));
  });

  test('chunks never exceed the 512-byte ATT attribute limit even at mtu 517', () {
    final chunks = splitChunks(Uint8List(2000), 517);
    expect(chunks.map((c) => c.length).reduce((a, b) => a > b ? a : b), 512);
    expect(chunkPayloadSize(517), 511);
    expect(chunkPayloadSize(23), 19);
  });

  test('assembler drops messages over the size limit', () {
    final a = ChunkAssembler(maxMessage: 100);
    Uint8List? result;
    for (final c in splitChunks(Uint8List(150), 23)) {
      result = a.feed(c);
    }
    expect(result, isNull);
    final small = bytes('{"t":"ping"}');
    expect(a.feed(splitChunks(small, 23)[0]), small);
  });
}
