# Secret Message TEST relay

A small WebSocket relay, so that two phones running a Secret Message TEST build can chat when they
cannot reach each other directly (mobile data, different Wi-Fi networks, different places). It is
a Cloudflare Worker with one Durable Object per chat room, deployed to the owner's own Cloudflare
account on the free plan. The owner approved this test deployment on 17 October 2026. It is **not
security-reviewed**, and it is used only by unpublished TEST builds.

## What it does

1. The phone that starts a chat picks a random room (16 random bytes) and opens
   `wss://…/v1/rooms/<room>?role=host`. The relay answers `waiting`.
2. The other phone opens the invite and connects with `role=guest`. Both phones get `paired`.
3. From then on the relay forwards every binary WebSocket message from one phone to the other,
   unchanged and in order. Text messages are not forwarded.
4. When either phone leaves, the relay closes the other phone's connection (code 4001). A room
   is used once.

Limits: one host and one guest per room (others get HTTP 409; a guest with no host gets 404); a
host waits at most 10 minutes; a paired room closes after 75 minutes (the app's chat limit is 60);
messages over 1 MiB, or data sent before the other phone joined, close the room (code 4003).

## Doorbells (from app 1.1.0)

So that remembered phones can call each other without Google services, each phone that is "ready
for chats" keeps one WebSocket open to its own inbox, one Durable Object per inbox:

- `wss://…/v1/inbox/<id>` with the header `x-inbox-key: <secret>` (64 hex digits). The relay checks
  that `<id>` is the first 16 bytes of SHA-256("secretmessage inbox id v1" ‖ secret), so only the
  phone that made the inbox can listen to it (HTTP 403 otherwise). It answers `ready`; a newer
  connection replaces an older one (code 4004); the text `ping` is answered with `pong` by the
  runtime without waking the inbox.
- `POST /v1/inbox/<id>/ring` with at most 1 KiB of bytes passes them to the listening phone
  unchanged: 200 delivered, 404 not reachable (nobody listening), 429 after 10 rings in a minute,
  400 empty, 413 too large. The app's ring is 76 bytes, encrypted with a key that only the two
  phones share (derived from doorbell cards they exchanged inside an MLS chat); it carries the
  room and token of a new chat. The relay cannot read or fake it.
- `GET /v1/inbox/<id>/presence` answers `online` or `offline`.

Inbox IDs are shared only inside MLS chats, with remembered phones. Rings are never stored.

## What it can and cannot see

Everything the phones send after setup is inside their MLS session, so the relay cannot read
messages, drawings or pictures. If a relay tried to stand in for the other phone, the two phones
would show different safety codes. The relay does see, like any server:

- both phones' IP addresses, when they connect, and how much data they send and when;
- the room ID and the start of the session in the clear: the invite token, the device IDs in the
  MLS credentials, and the public keys exchanged before the safety-code check.
- for doorbells: which inbox is connected from which IP address and when, and that a ring went from
  one IP address to an inbox (not who sent it or what it says).

It stores nothing and writes no logs (`observability` is off; the rooms keep only an alarm for
their time limit). Cloudflare itself keeps its usual account-level request statistics.

## Deploying

`.github/workflows/secret-message-relay.yml` runs on changes here: it runs
`test/relay-test.mjs` against the relay in the local Workers runtime (`wrangler dev`), then
deploys with the repository secret `CLOUDFLARE_API_TOKEN` (an account API token from the template
"Edit Cloudflare Workers"; wrangler finds the token's one account by itself, so the
`CLOUDFLARE_ACCOUNT_ID` secret is not used), prints the relay address as a `relay-url` notice and
runs the same checks against the deployed relay. Nobody needs to paste a token anywhere else.

Local check: `npm ci`, `npm run dev` in one terminal, then
`node test/relay-test.mjs ws://127.0.0.1:8787`.
