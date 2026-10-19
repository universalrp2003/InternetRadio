// Secret Message TEST relay (owner-approved test deployment, 17 October 2026).
//
// Two phones that cannot reach each other directly (mobile data, different networks) each open a
// WebSocket to this Worker for the same random room. Once both are in, the room forwards binary
// messages from one phone to the other, unchanged and in order. It never reads, stores or logs
// them: everything the phones send after setup is inside their MLS session, and if a relay tried
// to stand in for the other phone the two phones would show different safety codes.
//
// One Durable Object per room holds the two WebSockets with the hibernation API, so an idle room
// costs nothing. A room is single-use: when either phone leaves, the other is disconnected.
//
// From app 1.1.0 each phone that is "ready for chats" also keeps one WebSocket open to its own
// inbox (one Durable Object per inbox, also hibernating). A remembered phone that wants to chat
// posts a small ring there, encrypted with a key only the two phones share; the inbox passes it
// to the open socket and keeps nothing. If nobody is listening, the caller hears "not reachable".
import { DurableObject } from "cloudflare:workers";

const PROTOCOL = "secretmessage-relay/1";

/** Rooms are 16 random bytes from the host phone, as 32 lowercase hex digits. */
const ROOM_PATH = /^\/v1\/rooms\/([0-9a-f]{32})$/;

/** An inbox ID is SHA-256("secretmessage inbox id v1" ‖ secret), first 16 bytes, as hex. */
const INBOX_PATH = /^\/v1\/inbox\/([0-9a-f]{32})(?:\/(ring|presence))?$/;
const INBOX_SECRET = /^[0-9a-f]{64}$/;
const INBOX_LABEL = "secretmessage inbox id v1";

/** A ring is 76 bytes; anything over 1 KiB is refused. */
const MAX_RING_BYTES = 1024;

/** Rings per inbox per minute, so a leaked inbox ID cannot be used to flood a phone. */
const RINGS_PER_MINUTE = 10;

/** A host may wait this long for the other phone to join. */
const HOST_WAIT_MS = 10 * 60 * 1000;

/** A paired room closes after this, longer than the app's 60-minute chat limit. */
const ROOM_LIFETIME_MS = 75 * 60 * 1000;

/** The app sends at most 64 KiB per message; anything far larger is refused. */
const MAX_MESSAGE_BYTES = 1024 * 1024;

/**
 * Close codes the app shows to the person (4000–4999 are free for applications). A Worker's main
 * module may export only its handler and Durable Object classes, so these stay module-private.
 */
const CLOSE_PARTNER_LEFT = 4001;
const CLOSE_TIME_LIMIT = 4002;
const CLOSE_PROTOCOL = 4003;
const CLOSE_REPLACED = 4004;

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/") {
      return plain(200, `${PROTOCOL}\n`);
    }
    const inbox = INBOX_PATH.exec(url.pathname);
    if (inbox) {
      return inboxRequest(request, env, inbox[1], inbox[2]);
    }
    const room = ROOM_PATH.exec(url.pathname)?.[1];
    if (!room) {
      return plain(404, "Not found\n");
    }
    if (request.method !== "GET" || request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return plain(426, "Expected a WebSocket upgrade\n");
    }
    const role = url.searchParams.get("role");
    if (role !== "host" && role !== "guest") {
      return plain(400, "Unknown role\n");
    }
    return env.ROOMS.getByName(room).fetch(request);
  },
};

/** One chat between two phones: the host, who created the invite, and the guest who opened it. */
export class Room extends DurableObject {
  async fetch(request) {
    const role = new URL(request.url).searchParams.get("role");
    const hosts = this.open("host").length;
    const guests = this.open("guest").length;
    if (role === "host" && hosts + guests > 0) {
      return plain(409, "This room is already in use\n");
    }
    if (role === "guest" && hosts === 0) {
      return plain(404, "Nobody is waiting in this room\n");
    }
    if (role === "guest" && guests > 0) {
      return plain(409, "This room is already in use\n");
    }

    const [client, server] = Object.values(new WebSocketPair());
    this.ctx.acceptWebSocket(server, [role]);
    if (role === "host") {
      server.send("waiting");
      await this.ctx.storage.setAlarm(Date.now() + HOST_WAIT_MS);
    } else {
      for (const socket of this.ctx.getWebSockets()) {
        socket.send("paired");
      }
      await this.ctx.storage.setAlarm(Date.now() + ROOM_LIFETIME_MS);
    }
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(socket, message) {
    if (typeof message === "string") {
      // The phones send only binary data; text is not forwarded.
      return;
    }
    if (message.byteLength > MAX_MESSAGE_BYTES) {
      this.closeAll(CLOSE_PROTOCOL, "Message too large");
      return;
    }
    const role = this.ctx.getTags(socket)[0];
    const partner = this.open(role === "host" ? "guest" : "host")[0];
    if (!partner) {
      // Nothing may be sent before both phones are in; dropping it would corrupt the stream.
      this.closeAll(CLOSE_PROTOCOL, "Data before the other phone joined");
      return;
    }
    partner.send(message);
  }

  async webSocketClose(socket) {
    // Answer the leaving phone's close frame (Cloudflare documents this as safe even where the
    // runtime replies by itself) and tell the other phone that its partner left.
    try {
      socket.close(1000, "Closed");
    } catch {
      // Already closed.
    }
    this.closeAll(CLOSE_PARTNER_LEFT, "The other phone left", socket);
  }

  async webSocketError(socket) {
    this.closeAll(CLOSE_PARTNER_LEFT, "The other phone left", socket);
  }

  async alarm() {
    this.closeAll(CLOSE_TIME_LIMIT, "Time limit");
  }

  /** This room's sockets with `tag` that are still open; a phone that just left is not counted. */
  open(tag) {
    return this.ctx.getWebSockets(tag).filter((socket) => socket.readyState === WebSocket.OPEN);
  }

  closeAll(code, reason, except = null) {
    for (const socket of this.ctx.getWebSockets()) {
      if (socket === except) {
        continue;
      }
      try {
        socket.close(code, reason);
      } catch {
        // Already closing.
      }
    }
  }
}

/** Routes an inbox request: the owner's WebSocket, a ring, or a presence check. */
async function inboxRequest(request, env, id, action) {
  const inbox = env.INBOXES.getByName(id);
  if (action === "ring") {
    if (request.method !== "POST") {
      return plain(405, "Use POST\n");
    }
    if (Number(request.headers.get("content-length") ?? "0") > MAX_RING_BYTES) {
      return plain(413, "Ring too large\n");
    }
    const ring = await request.arrayBuffer();
    if (ring.byteLength === 0) {
      return plain(400, "Empty ring\n");
    }
    if (ring.byteLength > MAX_RING_BYTES) {
      return plain(413, "Ring too large\n");
    }
    return inbox.fetch("https://inbox/ring", { method: "POST", body: ring });
  }
  if (action === "presence") {
    if (request.method !== "GET") {
      return plain(405, "Use GET\n");
    }
    return inbox.fetch("https://inbox/presence");
  }
  if (request.method !== "GET" || request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
    return plain(426, "Expected a WebSocket upgrade\n");
  }
  const secret = request.headers.get("x-inbox-key") ?? "";
  if (!INBOX_SECRET.test(secret) || (await inboxIdFor(secret)) !== id) {
    return plain(403, "This key does not open this inbox\n");
  }
  return inbox.fetch(request);
}

async function inboxIdFor(secretHex) {
  const label = new TextEncoder().encode(INBOX_LABEL);
  const data = new Uint8Array(label.length + 32);
  data.set(label);
  for (let index = 0; index < 32; index += 1) {
    data[label.length + index] = parseInt(secretHex.slice(index * 2, index * 2 + 2), 16);
  }
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", data));
  return Array.from(digest.slice(0, 16), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

/**
 * One phone's doorbell. The phone keeps a WebSocket open here while it is ready for chats; a
 * newer connection replaces an older one (4004). Rings are passed to the open socket unchanged
 * and never stored. "ping" is answered with "pong" by the runtime without waking this object.
 */
export class Inbox extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
    /** When recent rings arrived (memory only; reset whenever the object sleeps). */
    this.recentRings = [];
  }

  async fetch(request) {
    const path = new URL(request.url).pathname;
    if (path === "/ring") {
      return this.ring(await request.arrayBuffer());
    }
    if (path === "/presence") {
      return plain(200, this.owner() ? "online\n" : "offline\n");
    }
    for (const older of this.ctx.getWebSockets("owner")) {
      try {
        older.close(CLOSE_REPLACED, "Replaced by a newer connection");
      } catch {
        // Already closing.
      }
    }
    const [client, server] = Object.values(new WebSocketPair());
    this.ctx.acceptWebSocket(server, ["owner"]);
    server.send("ready");
    return new Response(null, { status: 101, webSocket: client });
  }

  ring(bytes) {
    const now = Date.now();
    this.recentRings = this.recentRings.filter((at) => now - at < 60_000);
    if (this.recentRings.length >= RINGS_PER_MINUTE) {
      return plain(429, "Too many rings; try again in a minute\n");
    }
    this.recentRings.push(now);
    const owner = this.owner();
    if (!owner) {
      return plain(404, "Not reachable\n");
    }
    owner.send(bytes);
    return plain(200, "delivered\n");
  }

  /** The newest open owner socket, or null if the phone is not ready for chats now. */
  owner() {
    const open = this.ctx.getWebSockets("owner").filter((socket) => socket.readyState === WebSocket.OPEN);
    return open.length > 0 ? open[open.length - 1] : null;
  }

  async webSocketMessage() {
    // The phone sends only pings, which the runtime answers; anything else is ignored.
  }

  async webSocketClose(socket) {
    try {
      socket.close(1000, "Closed");
    } catch {
      // Already closed.
    }
  }

  async webSocketError() {
    // The phone reconnects by itself.
  }
}

function plain(status, body) {
  return new Response(body, {
    status,
    headers: { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" },
  });
}
