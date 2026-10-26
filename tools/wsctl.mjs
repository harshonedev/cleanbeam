#!/usr/bin/env node
// Smoke client for the Clean Beam Remote WebSocket transport.
//   node tools/wsctl.mjs <ip> <token> '<json>' ['<json>' ...]
//   node tools/wsctl.mjs <ip> --pair <pin> '<json>' ...
const [,, ip, a, b, ...rest] = process.argv;
if (!ip || !a) { console.error("usage: wsctl.mjs <ip> (<token> | --pair <pin>) '<json>'..."); process.exit(2); }
const pairing = a === "--pair";
const first = pairing ? { t: "pair", code: b } : { t: "auth", token: a };
const msgs = pairing ? rest : [b, ...rest].filter(Boolean);
const ws = new WebSocket(`ws://${ip}:7272/`);
ws.onopen = () => {
  ws.send(JSON.stringify(first));
  for (const m of msgs) ws.send(m);
  setTimeout(() => { ws.close(); process.exit(0); }, 2000);
};
ws.onmessage = (e) => console.log("<", e.data);
ws.onerror = (e) => { console.error("error", e.message ?? e); process.exit(1); };
ws.onclose = (e) => console.log("closed", e.code);
