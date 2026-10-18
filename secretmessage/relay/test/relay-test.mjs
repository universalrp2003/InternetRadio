// Checks the Secret Message TEST relay end to end over real WebSockets:
//   node test/relay-test.mjs ws://127.0.0.1:8787            (wrangler dev)
//   node test/relay-test.mjs wss://secretmessage-relay.<subdomain>.workers.dev
// Every run uses fresh random rooms and sends only random test bytes.
import assert from "node:assert/strict";
import { createHash, randomBytes } from "node:crypto";
import { WebSocket } from "ws";

const base = (process.argv[2] ?? "").replace(/\/+$/, "");
if (!/^wss?:\/\/[^/]+$/.test(base)) {
  console.error("Usage: node test/relay-test.mjs <ws://host:port | wss://host>");
  process.exit(2);
}
const httpBase = base.replace(/^ws/, "http");
const WAIT_MS = 15_000;
const CLOSE_PARTNER_LEFT = 4001;
const CLOSE_PROTOCOL = 4003;
const CLOSE_TOO_BIG = 1009;

const CLOSE_REPLACED = 4004;

const newRoom = () => randomBytes(16).toString("hex");
const roomUrl = (room, role) => `${base}/v1/rooms/${room}?role=${role}`;

/** A fresh inbox as the app makes one: a random secret and the ID the relay derives from it. */
function newInbox() {
  const secret = randomBytes(32);
  const id = createHash("sha256").update("secretmessage inbox id v1").update(secret).digest().subarray(0, 16).toString("hex");
  return { id, secret: secret.toString("hex") };
}
const inboxUrl = (id) => `${base}/v1/inbox/${id}`;
const ring = (id, body) => fetch(`${httpBase}/v1/inbox/${id}/ring`, { method: "POST", body });
const presence = async (id) => (await (await fetch(`${httpBase}/v1/inbox/${id}/presence`)).text()).trim();

/** One phone's WebSocket, collecting what it receives. */
class Phone {
  constructor(url, headers = {}) {
    this.texts = [];
    this.chunks = [];
    this.received = 0;
    this.waiters = new Set();
    this.socket = new WebSocket(url, { handshakeTimeout: WAIT_MS, headers });
    this.opened = new Promise((resolve, reject) => {
      this.socket.once("open", resolve);
      this.socket.once("unexpected-response", (_request, response) => {
        reject(Object.assign(new Error(`HTTP ${response.statusCode}`), { status: response.statusCode }));
        response.resume();
      });
      this.socket.once("error", reject);
    });
    this.opened.catch(() => {});
    this.closed = new Promise((resolve) => {
      this.socket.once("close", (code, reason) => resolve({ code, reason: reason.toString() }));
    });
    this.socket.on("message", (data, isBinary) => {
      if (isBinary) {
        this.chunks.push(data);
        this.received += data.length;
      } else {
        this.texts.push(data.toString());
      }
      for (const wake of this.waiters) wake();
    });
  }

  async until(check, what) {
    const deadline = Date.now() + WAIT_MS;
    while (!check()) {
      if (Date.now() > deadline) throw new Error(`Timed out waiting for ${what}`);
      await new Promise((resolve) => {
        this.waiters.add(resolve);
        setTimeout(resolve, 200);
      }).finally(() => this.waiters.clear());
    }
  }

  async text(expected) {
    await this.until(() => this.texts.includes(expected), `"${expected}"`);
  }

  async bytes(total) {
    await this.until(() => this.received >= total, `${total} bytes`);
    return Buffer.concat(this.chunks);
  }

  /**
   * The close code, as soon as the relay's close frame arrives (as the app's onClosing sees it);
   * `ws` itself reports the close only once the TCP connection ends, which can take seconds.
   */
  async closedWith() {
    const frame = (async () => {
      // `_closeFrameReceived` and `_closeCode` are internal fields of the pinned ws 8.22.0.
      await this.until(
        () => this.socket._closeFrameReceived || this.socket.readyState === WebSocket.CLOSED,
        "a close frame",
      );
      return this.socket._closeFrameReceived ? { code: this.socket._closeCode, reason: "" } : this.closed;
    })();
    frame.catch(() => {});
    return withTimeout(Promise.race([this.closed, frame]), "the connection to close");
  }

  close() {
    this.socket.close(1000, "test over");
  }
}

function withTimeout(promise, what) {
  let timer;
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error(`Timed out waiting for ${what}`)), WAIT_MS);
    }),
  ]).finally(() => clearTimeout(timer));
}

async function refused(url, headers = {}) {
  const phone = new Phone(url, headers);
  try {
    await phone.opened;
  } catch (error) {
    return error.status;
  }
  phone.close();
  return 101;
}

/** Sends `data` in uneven pieces, as the app's bridge does with whatever it reads. */
function sendInPieces(phone, data) {
  let offset = 0;
  let step = 1;
  while (offset < data.length) {
    const size = Math.min(data.length - offset, (step * 7919) % 65_536 || 1);
    phone.socket.send(data.subarray(offset, offset + size), { binary: true });
    offset += size;
    step += 1;
  }
}

async function paired() {
  const room = newRoom();
  const host = new Phone(roomUrl(room, "host"));
  await host.opened;
  await host.text("waiting");
  const guest = new Phone(roomUrl(room, "guest"));
  await guest.opened;
  await Promise.all([host.text("paired"), guest.text("paired")]);
  return { room, host, guest };
}

const checks = [
  ["the front page names the relay protocol", async () => {
    const response = await fetch(`${httpBase}/`);
    assert.equal(response.status, 200);
    assert.match(await response.text(), /^secretmessage-relay\/1/);
  }],
  ["plain requests, unknown paths and roles are refused", async () => {
    assert.equal((await fetch(`${httpBase}/v1/rooms/${newRoom()}?role=host`)).status, 426);
    assert.equal(await refused(`${base}/v1/rooms/not-a-room?role=host`), 404);
    assert.equal(await refused(`${base}/v1/rooms/${newRoom().toUpperCase()}?role=host`), 404);
    assert.equal(await refused(roomUrl(newRoom(), "admin")), 400);
  }],
  ["a guest cannot open a room nobody is waiting in", async () => {
    assert.equal(await refused(roomUrl(newRoom(), "guest")), 404);
  }],
  ["two phones pair, a third is refused, and bytes pass unchanged both ways", async () => {
    const { room, host, guest } = await paired();
    assert.equal(await refused(roomUrl(room, "host")), 409);
    assert.equal(await refused(roomUrl(room, "guest")), 409);

    host.socket.send("text is not forwarded");
    const toGuest = randomBytes(300_000);
    const toHost = randomBytes(120_000);
    sendInPieces(host, toGuest);
    sendInPieces(guest, toHost);
    assert.ok((await guest.bytes(toGuest.length)).equals(toGuest), "guest received the host's bytes in order");
    assert.ok((await host.bytes(toHost.length)).equals(toHost), "host received the guest's bytes in order");
    assert.deepEqual(guest.texts, ["paired"]);

    const pong = new Promise((resolve) => host.socket.once("pong", resolve));
    host.socket.ping();
    await withTimeout(pong, "a pong");

    host.close();
    const closed = await guest.closedWith();
    assert.equal(closed.code, CLOSE_PARTNER_LEFT);
  }],
  ["a phone that leaves while waiting frees nothing for strangers", async () => {
    const room = newRoom();
    const host = new Phone(roomUrl(room, "host"));
    await host.opened;
    await host.text("waiting");
    host.close();
    await host.closedWith();
    assert.equal(await refused(roomUrl(room, "guest")), 404);
  }],
  ["data before the other phone joins closes the room", async () => {
    const room = newRoom();
    const host = new Phone(roomUrl(room, "host"));
    await host.opened;
    await host.text("waiting");
    host.socket.send(randomBytes(16), { binary: true });
    assert.equal((await host.closedWith()).code, CLOSE_PROTOCOL);
  }],
  ["an oversized message closes the room for both phones", async () => {
    const { host, guest } = await paired();
    host.socket.send(randomBytes(1024 * 1024 + 1), { binary: true });
    const [hostClosed, guestClosed] = await Promise.all([host.closedWith(), guest.closedWith()]);
    // The relay closes the room itself (4003), unless Cloudflare's edge already cut the message
    // (1009, too big), in which case the other phone hears that its partner left (4001).
    assert.ok([CLOSE_PROTOCOL, CLOSE_TOO_BIG].includes(hostClosed.code), `host: ${hostClosed.code}`);
    assert.ok([CLOSE_PROTOCOL, CLOSE_PARTNER_LEFT].includes(guestClosed.code), `guest: ${guestClosed.code}`);
  }],
  ["an inbox opens only with its own key", async () => {
    const inbox = newInbox();
    const other = newInbox();
    assert.equal(await refused(inboxUrl(inbox.id)), 403);
    assert.equal(await refused(inboxUrl(inbox.id), { "x-inbox-key": other.secret }), 403);
    assert.equal(await refused(inboxUrl(inbox.id), { "x-inbox-key": inbox.secret.toUpperCase() }), 403);
    assert.equal((await fetch(`${httpBase}/v1/inbox/${inbox.id}`)).status, 426);
    assert.equal(await presence(inbox.id), "offline");
  }],
  ["a ring reaches the ready phone unchanged, and pings are answered", async () => {
    const inbox = newInbox();
    const owner = new Phone(inboxUrl(inbox.id), { "x-inbox-key": inbox.secret });
    await owner.opened;
    await owner.text("ready");
    assert.equal(await presence(inbox.id), "online");
    const sent = randomBytes(76);
    const response = await ring(inbox.id, sent);
    assert.equal(response.status, 200);
    assert.ok((await owner.bytes(sent.length)).equals(sent), "the ring arrived unchanged");
    owner.socket.send("ping");
    await owner.text("pong");
    owner.close();
    await owner.closedWith();
    assert.equal(await presence(inbox.id), "offline");
    assert.equal((await ring(inbox.id, randomBytes(76))).status, 404);
  }],
  ["a newer connection replaces the older one", async () => {
    const inbox = newInbox();
    const first = new Phone(inboxUrl(inbox.id), { "x-inbox-key": inbox.secret });
    await first.opened;
    await first.text("ready");
    const second = new Phone(inboxUrl(inbox.id), { "x-inbox-key": inbox.secret });
    await second.opened;
    await second.text("ready");
    assert.equal((await first.closedWith()).code, CLOSE_REPLACED);
    assert.equal(await presence(inbox.id), "online");
    const sent = randomBytes(76);
    assert.equal((await ring(inbox.id, sent)).status, 200);
    assert.ok((await second.bytes(sent.length)).equals(sent), "the newer connection got the ring");
    second.close();
  }],
  ["empty, oversized and repeated rings are refused", async () => {
    const inbox = newInbox();
    assert.equal((await ring(inbox.id, new Uint8Array(0))).status, 400);
    assert.equal((await ring(inbox.id, randomBytes(1025))).status, 413);
    assert.equal((await fetch(`${httpBase}/v1/inbox/${inbox.id}/ring`)).status, 405);
    const statuses = [];
    for (let index = 0; index < 11; index += 1) {
      statuses.push((await ring(inbox.id, randomBytes(76))).status);
    }
    assert.deepEqual(statuses.slice(0, 10), Array(10).fill(404));
    assert.equal(statuses[10], 429);
  }],
];

let failed = 0;
for (const [name, check] of checks) {
  const started = Date.now();
  try {
    await check();
    console.log(`ok - ${name} (${Date.now() - started} ms)`);
  } catch (error) {
    failed += 1;
    console.log(`not ok - ${name}: ${error.message}`);
  }
}
console.log(`${checks.length - failed}/${checks.length} relay checks passed against ${base}`);
process.exit(failed === 0 ? 0 : 1);
