import 'package:flutter_test/flutter_test.dart';
import 'package:remote_app/pairing/pairing.dart';

void main() {
  test('parses the projector QR payload', () {
    final p = Pairing.fromQrJson('{"v":1,"name":"EGate Atom3X","ip":"192.168.10.52","port":7272,"token":"abc"}');
    expect(p, isNotNull);
    expect(p!.name, 'EGate Atom3X');
    expect(p.ip, '192.168.10.52');
    expect(p.port, 7272);
    expect(p.token, 'abc');
  });

  test('rejects foreign or malformed QR content', () {
    expect(Pairing.fromQrJson('https://example.com'), isNull);
    expect(Pairing.fromQrJson('{"v":2,"token":"x"}'), isNull);
    expect(Pairing.fromQrJson('{"v":1}'), isNull);
  });

  test('round-trips through json', () {
    final p = Pairing(name: 'n', ip: '1.2.3.4', port: 7272, token: 't');
    expect(Pairing.fromJson(p.toJson()).token, 't');
  });
}
