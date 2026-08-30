# BCCU BLE Protocol

The Bluetooth LE protocol for the KTM/Husqvarna Gen-3 "connected" dash (BCCU). This is the
write-up referenced by code comments across `ble/`. It documents the GATT layout, wire
formats, session crypto, the pairing/authentication handshake, and the telemetry RPC channel.

> **Provenance.** Unless noted, everything here is **Confirmed byte-exact** from decompiled
> source of two independent apps — the third-party "BikeConnect" app and the official KTM
> Connect SDK (`com.ktm.mobsdk.*`) — and validated against a live GATT dump of bike
> `KTM3638` on a KTM 390 Adventure. Items only seen in the live dump (not in either app's
> source) are labelled **Reverse-engineered**. Source of truth in this repo:
> `ble/BccuProtocol.kt`, `ble/BccuCrypto.kt`, `ble/BccuConnectionService.kt`,
> `ble/TelemetrySchema.kt`.

All multi-byte integers in the telemetry/RPC path are **little-endian**.

---

## 1. GATT services & characteristics

UUIDs follow the template `71ced1ac-XXXX-44f5-9454-806ff70b3e02` (only the 16-bit `XXXX`
shown below), except the standard SIG Device Information service.

### 1.1 MAIN service — `0700`
The navigation + notification + auth surface.

| Char | UUID `XXXX` | Dir | Purpose |
|---|---|---|---|
| AUTH_REQUEST | `0701` | indicate | Bike → phone handshake messages (m1, control commands) |
| AUTH_REPLY | `0702` | write | Phone → bike handshake replies (m2, control acks) |
| NAVIGATION_STATE | `0703` | write | `[flags][volume]` — guidanceOn / gpsIconOn |
| TURN_ICON | `0704` | write | `[visibility][iconByte]` — the maneuver glyph enum |
| TURN_DISTANCE | `0705` | write | `[visibility][UTF-8]` ≤ 8 chars, e.g. "110 m" |
| TURN_INFO | `0706` | write | `[visibility][UTF-8]` ≤ 16 chars (secondary maneuver text) |
| TURN_ROAD | `0707` | write | `[visibility][UTF-8]` ≤ 32 chars, e.g. "towards 1st Cross Rd" |
| ETA | `0708` | write | `[visibility][UTF-8]` ≤ 8 chars, e.g. "12:45" |
| REMAINING_DISTANCE | `0709` | write | `[visibility][UTF-8]` ≤ 8 chars, e.g. "12 km" |
| NOTIFICATION | `070a` | write | `[visibility][icon][UTF-8 ≤ 16]` — bottom banner |
| TBT_NAV_REQUEST | `070b` | notify | (subscribed; turn-by-turn request channel) |
| TBT_NAV_RESPONSE | `070c` | — | turn-by-turn response channel |

The **center guidance view** (TURN_ICON + TURN_DISTANCE/INFO/ROAD + ETA + REMAINING_DISTANCE)
is a different display region from the **bottom banner** (NOTIFICATION). The dash requires
`NAVIGATION_STATE.guidanceOn = ON` before it renders center-guidance content.

### 1.2 RCM service — `0100`
| Char | UUID `XXXX` | Dir | Purpose |
|---|---|---|---|
| RCM_REMOTE_CONTROL | `0103` | indicate | Handlebar button state |

### 1.3 PRPC (telemetry RPC) service — `0600`
| Char | UUID `XXXX` | Dir | Purpose |
|---|---|---|---|
| PRPC_REQUEST | `0601` | write | RPC requests (get/set/telemetry configure/control) |
| PRPC_RESPONSE | `0602` | notify | RPC responses |
| PRPC_NOTIFICATION | `0603` | notify | Telemetry data notifications (notifyCode 7) |

Confirmed from `BleRpcTransport.java`. Same session `frame()` + AES scheme as MAIN.

### 1.4 BASE service — `0000`
| Char | UUID `XXXX` | Dir | Purpose |
|---|---|---|---|
| BASE_GET_VIN_REQUEST | `0001` | write | Request VIN |
| BASE_VIN | `0002` | notify/read | VIN |

### 1.5 Standard Device Information service
`0000180a-0000-1000-8000-00805f9b34fb` — model / serial / firmware / hardware / software
revision.

### 1.6 Undocumented services (Reverse-engineered)
Seen in the live GATT dump but absent from both apps' source: `0200` (six R/W/w/N/I chars
`0201`–`0206` — the best remaining lead for streaming telemetry on a bike without PRPC),
`0300`. Not used by this app.

---

## 2. Enums & payload builders

From `BccuProtocol.kt`.

### 2.1 `TurnIcon` (byte) — 58 values
`UNKNOWN(0)`, `UNDEFINED(1)`, `GO_STRAIGHT(2)`, `UTURN_RIGHT(3)`, `UTURN_LEFT(4)`,
`KEEP_RIGHT(5)`, `LIGHT_RIGHT(6)`, `QUITE_RIGHT(7)`, `HEAVY_RIGHT(8)`, `KEEP_MIDDLE(9)`,
`KEEP_LEFT(10)`, `LIGHT_LEFT(11)`, `QUITE_LEFT(12)`, `HEAVY_LEFT(13)`,
`ENTER_HIGHWAY_RIGHT_LANE(14)`, `ENTER_HIGHWAY_LEFT_LANE(15)`,
`LEAVE_HIGHWAY_RIGHT_LANE(16)`, `LEAVE_HIGHWAY_LEFT_LANE(17)`,
`HIGHWAY_KEEP_RIGHT(18)`, `HIGHWAY_KEEP_LEFT(19)`,
`START(20)`, `END(21)`, `FERRY(22)`, `PASS_STATION(23)`, `HEAD_TO(24)`, `CHANGE_LINE(25)`,
`RAB_SECT_1_RH(26)`…`RAB_SECT_16_RH(41)` (right-hand roundabout sections),
`RAB_SECT_1_LH(42)`…`RAB_SECT_16_LH(57)` (left-hand roundabout sections).

Roundabout section `n` = base + (n−1); RH base 26, LH base 42; `n` clamped 1..16. Unknown
codes do not render — the dash draws its own glyphs, so we never emit a wrong arrow (map
UNKNOWN → `UNDEFINED`).

### 2.2 `NotificationIcon` (byte)
`UNKNOWN(0)`, `NOTIFICATION_REROUTING(1)`, `NOTIFICATION_WAYPOINT(2)`, `TARGET_REACHED(3)`,
`GPS_LOST(4)`, `WARNING(5)`, `INFORMATION(6)`, `SPEED(7)`.
Hardware calibration (Symbol Testing screen) found only `NOTIFICATION_REROUTING` and
`NOTIFICATION_WAYPOINT` actually render on this dash's banner; `NOTIFICATION_WAYPOINT` is the
settled default.

### 2.3 `Visibility` (byte) — **not** a 0/1 boolean
`UNKNOWN(-1)`, `FULL(3)`, `HALF(2)`, `OFF(1)`. Confirmed from
`com.ktm.mob.services.etbt.Visibility.binary()`.

### 2.4 `OnOff` (byte): `OFF(0)`, `ON(1)`.

### 2.5 Payload shapes
- **TURN_ICON**: `[visibility][iconByte]`.
- **Label chars** (TURN_DISTANCE/INFO/ROAD/ETA/REMAINING_DISTANCE): `[visibility] + UTF-8`,
  ellipsized to the char cap via `StrElipsed.elipse()` (fits→as-is; else `substring(0,
  maxLen-3) + "..."`; operates on UTF-16 length). Caps: DISTANCE 8, INFO 16, ROAD 32, ETA 8,
  REMAINING 8.
- **NOTIFICATION**: `[visibility][icon] + UTF-8`, hard-truncated to 16 chars (no ellipsis).
- **NAVIGATION_STATE**: `[byte0: bit0=guidanceOn bit1=gpsIconOn][byte1: volume 0–100, 255=unset]`.

---

## 3. Session crypto

From `BccuCrypto.kt`. Cipher: **AES-128-CBC, NoPadding**. Two planes:

### 3.1 Control plane (handshake)
Raw single 16-byte block AES, **no framing**. A control message is 16 bytes:
`byte[2]=0xFF` marker, `byte[4]=command`, `byte[6]=0x01` version, rest random.

### 3.2 Data plane (guidance, notifications, RPC)
Every payload is wrapped by `frame()` before AES, and `unframe()` after decrypt:

```
frame(data) = [16 random prefix bytes][data][random fill][1 byte padLen]
  where total = data.len + 16 + padLen,  padLen = 16 - (data.len % 16)   (i.e. 1..16)
unframe(m) = drop first 16 bytes, then drop the last padLen bytes (padLen = last byte)
```

`encryptData = aes(frame(payload))`, `decryptData = unframe(aes(message, decrypt))`.

---

## 4. Pairing / authentication handshake

State machine in `BccuConnectionService.handleAuthMessage()`. All handshake traffic is on
AUTH_REQUEST (bike→phone, indications) / AUTH_REPLY (phone→bike, writes).

```
1. Link up → subscribe AUTH_REQUEST.
2. Bike → m1 (16-byte nonce).
   Phone: m2 = random 16 bytes; write m2 to AUTH_REPLY.
   Both derive the temporary IV + secret from (m1, m2):
     tempIv[0:8]  = m1[8:16]      tempIv[8:16]  = m2[0:8]
     tempSecret[0:8] = m2[8:16]   tempSecret[8:16] = m1[0:8]
3. Bike → encrypted control message (AES-decrypt with tempSecret/tempIv). byte[2] = command:
   - CMD_HELLO (0): echo HELLO back. The dash decides from its own bond memory whether to
     show the physical "add device" prompt — a bonded dash answers with GENERATE_KEYS in a
     few seconds and no prompt; a new device prompts and answers after the rider confirms
     (~30 s). (Reverse-engineered from field logs; echoing HELLO is the only reply it acts on.)
   - CMD_GENERATE_KEYS (1): derive the 16-key session pool (below); persist it per-MAC; reply
     with command 2.
   - key-select (16..31): the dash picks key index = cmd & 0x0F from the pool. Set it as the
     active session key, reply CMD_KEY_ACK_BASE|index (16|index) → **AUTHENTICATED**.
```

Reconnect after an ignition cycle skips GENERATE_KEYS: the dash keeps the pool it derived at
pairing and resumes by key-select alone. So the phone must **persist the pool per-MAC** (it
does, in encrypted prefs, surviving app updates/reinstalls) or every reconnect stalls on an
empty pool.

### 4.1 Session-key derivation (`deriveSessionKeys`)
On CMD_GENERATE_KEYS, from the decrypted challenge:

```
mirrored = 16-byte palindrome of challenge[8:16]:
  mirrored[i] = mirrored[15-i] = challenge[8+i]   for i in 0..7
base = [challenge, mirrored, tempIv, tempSecret]           (four 16-byte values)
for rot in 0..3:
   buf = concat(base[(0+rot)%4], base[(1+rot)%4], base[(2+rot)%4], base[(3+rot)%4])  # 64 bytes
   digest = SHA-512(buf)                                    # 64 bytes
   keys += [digest[0:16], digest[16:32], digest[32:48], digest[48:64]]
→ 16 keys total, in order. The dash selects among these by index at each session.
```

---

## 5. Telemetry RPC (PRPC)

Confirmed from `RPCRequestBuilder` / `RPCConfigureRequestBuilder` / `RPCControlRequestBuilder`
and `PRpcClient.parsePRpcNotification`. Requests/responses/notifications ride the same
`frame()`+AES data-plane scheme on the `0600` service.

### 5.1 Request header
`opcode (u16) · sid (u8) · payloadLength (u8) · payload…` (little-endian).

Opcodes: `GET_VALUE(1)`, `SET_VALUE(2)`, `TELEMETRY_CONFIGURE(3)`, `TELEMETRY_CONTROL(4)`.
- **Configure** payload (6 bytes): `datapointId (u16) · sampleRateMs (u32)`.
- **Control** payload (2 bytes): `command (u16)` — `STOP(0)`, `START(1)`, `RESET(2)`.

### 5.2 Telemetry notification (PRPC_NOTIFICATION)
`notifyCode (u16) · length (u8) · triples…`. `notifyCode == 7` is telemetry data; each triple
is `timestamp (u64) · datapointId (u16) · value (N bytes)`, where `N` is the datapoint's type
length from the schema.

### 5.3 Datapoint schema
Bundled JSON (`assets/bike_signals_contract.json`, from KTM Connect's `CUKT_bCCU.json`).
Loaded by `TelemetrySchema`. Type lengths (bytes): `T_UINT8/T_INT8/T_CHAR`=1,
`T_UINT16/T_INT16`=2, `T_UINT32/T_INT32/T_FLOAT`=4, `T_UINT64/T_INT64`=8. Each datapoint has
id, name, type, unit, default/min/max sample rate, and a `TELEMETRYABLE` flag.

---

## 6. Handlebar remote (RCM)

`parseRcmValue()` on the decrypted RCM_REMOTE_CONTROL notification: `active = v[16] == 0xFF`;
button bitmask at `v[17]` — `SET(bit0)`, `BACK(bit1)`, `DOWN(bit2)`, `UP(bit3)`.

---

## 7. App usage summary

The app writes only predefined fields: it never sends bitmaps/tiles (none exist in the
protocol — see the full-screen-map investigation in [`architecture.md`](architecture.md) §4.1).
Guidance is written via `sendNavigationState` / `sendTurnIcon` / `sendGuidance` /
`clearGuidance`; the notification banner via `sendNotification`. The GATT write queue coalesces
per-characteristic (latest-value-wins) so a degraded link can't build a backlog of stale
frames. These BLE internals are treated as frozen by the navigation revamp.
