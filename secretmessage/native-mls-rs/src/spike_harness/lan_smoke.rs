//! Test-only direct-LAN MLS exchange used by the standalone Android smoke APK.
//!
//! Each test install keeps one MLS signing identity, which the app stores wrapped with an Android
//! Keystore key; the MLS layer accepts any BasicCredential. Before any test message is exchanged,
//! both people compare a safety code derived from the RFC 9420 epoch authenticator of their shared
//! group and confirm it on both phones; a relaying device would hold two different groups, so the
//! phones would show different codes. If either person reports different codes, or does not
//! answer in time, both phones stop at once. After a confirmed session each phone remembers the
//! other phone's key and warns if that device later presents a different key. The two people can
//! then type short test messages to each other inside the same MLS group; they are shown on screen
//! only, never stored, and only their counts reach the report. This tests those steps. It is not
//! a production trust policy or a chat app, and it is not suitable for real/private messages.
//!
//! The Wi-Fi test build of the Secret Message app runs the same sessions under its own protocol
//! name and larger limits ([`WIFI_CHAT`]); its typed messages carry that app's encoded text and
//! drawings and its delivery and view notices. It is a test build too, not a reviewed messenger.

use super::{test_crypto_provider, test_signing_material, TEST_CIPHERSUITE};
use mls_rs::client_builder::MlsConfig;
use mls_rs::crypto::{SignaturePublicKey, SignatureSecretKey};
use mls_rs::group::ReceivedMessage;
use mls_rs::identity::basic::{BasicCredential, BasicIdentityProvider};
use mls_rs::identity::SigningIdentity;
use mls_rs::{Client, ExtensionList, MlsMessage};
use mls_rs_crypto_awslc::AwsLcCryptoProvider;
use std::collections::VecDeque;
use std::io::{ErrorKind, Read, Write};
use std::net::{Ipv4Addr, Shutdown, SocketAddr, SocketAddrV4, TcpListener, TcpStream};
#[cfg(unix)]
use std::os::unix::net::UnixStream;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{self, Receiver, Sender, TryRecvError};
use std::sync::Mutex;
use std::thread;
use std::time::{Duration, Instant};

const MAX_FRAME_BYTES: usize = 1024 * 1024;
const HOST_WAIT: Duration = Duration::from_secs(180);
const CONNECT_TIMEOUT: Duration = Duration::from_secs(15);
const IO_TIMEOUT: Duration = Duration::from_secs(45);
/// How long each person has to compare the safety code and answer.
const VERIFY_TIMEOUT: Duration = Duration::from_secs(120);
/// Four groups of five digits, each taken from a 40-bit chunk of the epoch authenticator.
const SAFETY_CODE_GROUPS: usize = 4;
const SAFETY_CODE_CHUNK_BYTES: usize = 5;
/// The BasicCredential label carries the install's device ID after this prefix.
const DEVICE_LABEL_PREFIX: &str = "secretmessage-lan-test:";
/// Device IDs are 8 random bytes chosen by the app, as 16 lowercase hexadecimal characters.
const DEVICE_ID_LENGTH: usize = 16;
const IDENTITY_FORMAT: &str = "v1";
const GUEST_TO_HOST: &[u8] = b"MLS-LAN-SMOKE-GUEST-TO-HOST-7fa19c";
const HOST_TO_GUEST: &[u8] = b"MLS-LAN-SMOKE-HOST-TO-GUEST-2ce840";
const GUEST_ACK: &[u8] = b"MLS-LAN-SMOKE-GUEST-ACK-55b0e2";
const CODE_CONFIRMED: &[u8] = b"MLS-LAN-SAFETY-CODE-CONFIRMED-3b9e41";
const CODE_REJECTED: &[u8] = b"MLS-LAN-SAFETY-CODE-REJECTED-c47d02";
const CODE_UNANSWERED: &[u8] = b"MLS-LAN-SAFETY-CODE-UNANSWERED-81f5a6";
/// The smoke test APK's joining phone starts its first frame with this, so a host can name a
/// version mismatch.
const PROTOCOL_TAG: &str = "secretmessage-lan-test/6";
const PROTOCOL_FAMILY: &str = "secretmessage-lan-test/";
/// How long a socket read waits before this phone's screen is checked again, both while the
/// people compare the safety code and during the typed test.
const SCREEN_POLL: Duration = Duration::from_millis(150);
/// The smoke test APK's typed test ends by itself this long after it opens.
const CHAT_LIMIT: Duration = Duration::from_secs(10 * 60);
/// After this phone's last message (the typed test's end marker, or an answer that stops the
/// test), how long to let the other phone read it before closing.
const END_LINGER: Duration = Duration::from_secs(2);
/// Longest smoke-test typed message, in UTF-8 bytes.
const MAX_TYPED_MESSAGE_BYTES: usize = 1000;
/// Most typed messages one phone may send in one smoke test.
const MAX_TYPED_MESSAGES: u32 = 200;
/// Largest frame accepted for a safety-code answer, and for a smoke-test typed message.
const MAX_SHORT_FRAME_BYTES: usize = 16 * 1024;
/// Kind bytes that start each typed-test payload inside an MLS application message.
const CHAT_TEXT: u8 = b'T';
const CHAT_ACK: u8 = b'A';
const CHAT_END: u8 = b'E';
const KEEP_APP_OPEN_HINT: &str = "Keep the test app open on both phones until the test ends: on Android 15 and later, an app loses its connections a few seconds after it leaves the screen (screen lock or switching apps).";
const LAN_PASS_STATUS: &str =
    "PASS: both people confirmed the same MLS safety code, then two separate devices exchanged fixed MLS test messages directly over local Wi-Fi. This phone now remembers the other phone's test key. TEST ONLY.";
const LOCAL_REJECTED_STATUS: &str =
    "STOPPED: you reported that the safety codes differ, so no test messages were exchanged. If the two phones really showed different codes, another device may have intercepted the connection. TEST ONLY.";
const PEER_REJECTED_STATUS: &str =
    "STOPPED: the other phone reported that the safety codes differ, so no test messages were exchanged. TEST ONLY.";
const LOCAL_UNANSWERED_STATUS: &str =
    "STOPPED: the safety code was not answered on this phone within two minutes, so no test messages were exchanged. TEST ONLY.";
const PEER_UNANSWERED_STATUS: &str =
    "STOPPED: the safety code was not answered on the other phone within two minutes, so no test messages were exchanged. TEST ONLY.";

/// The protocol name and limits of one app that runs these LAN sessions. The smoke test APK and
/// the Wi-Fi test build of the Secret Message app use different protocol names, so neither can
/// join the other's invite.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct LanProfile {
    /// The joining phone's first frame starts with this, so a host can name a version mismatch.
    protocol_tag: &'static str,
    /// Earlier and later versions of the same app share this prefix.
    protocol_family: &'static str,
    /// Longest typed message, in UTF-8 bytes.
    max_text_bytes: usize,
    /// Most typed messages one phone may send in one session.
    max_messages: u32,
    /// The typed session ends by itself this long after it opens.
    chat_limit: Duration,
    /// Largest frame accepted while the typed session is open.
    max_chat_frame_bytes: usize,
}

impl LanProfile {
    /// The protocol name this app's joining phones send.
    pub fn protocol_tag(&self) -> &'static str {
        self.protocol_tag
    }
}

/// The standalone smoke test APK: typed test messages of up to 300 characters on screen.
pub const SMOKE_TEST: LanProfile = LanProfile {
    protocol_tag: PROTOCOL_TAG,
    protocol_family: PROTOCOL_FAMILY,
    max_text_bytes: MAX_TYPED_MESSAGE_BYTES,
    max_messages: MAX_TYPED_MESSAGES,
    chat_limit: CHAT_LIMIT,
    max_chat_frame_bytes: MAX_SHORT_FRAME_BYTES,
};

/// The Wi-Fi test build of the Secret Message app. Its typed messages carry encoded text and
/// drawings, and every delivery and view notice is a message too, so its limits are larger.
pub const WIFI_CHAT: LanProfile = LanProfile {
    protocol_tag: "secretmessage-wifi-chat/1",
    protocol_family: "secretmessage-wifi-chat/",
    max_text_bytes: 96 * 1024,
    max_messages: 20_000,
    chat_limit: Duration::from_secs(60 * 60),
    max_chat_frame_bytes: 256 * 1024,
};

/// Set while a host waits for the other phone to join.
static HOST_WAITING: AtomicBool = AtomicBool::new(false);
/// Set by the screen to stop that wait early.
static HOST_CANCELLED: AtomicBool = AtomicBool::new(false);

/// One test install's persistent MLS signing identity.
struct LanIdentity {
    device_id: String,
    secret_key: SignatureSecretKey,
    public_key: SignaturePublicKey,
}

/// Whether this phone has verified the other phone's key before.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum PeerStatus {
    New,
    Known,
    KeyChanged,
}

impl PeerStatus {
    fn tag(self) -> &'static str {
        match self {
            PeerStatus::New => "NEW",
            PeerStatus::Known => "KNOWN",
            PeerStatus::KeyChanged => "CHANGED",
        }
    }
}

/// What the people compare, and what this phone knows about the other phone.
struct SafetyPrompt {
    code: String,
    peer_device: String,
    peer_status: PeerStatus,
}

impl SafetyPrompt {
    /// The form the test APK's screen reads: `code|status|device`.
    fn screen_text(&self) -> String {
        format!(
            "{}|{}|{}",
            self.code,
            self.peer_status.tag(),
            self.peer_device
        )
    }
}

/// A finished session's status and, after a verified PASS, the peer to remember.
struct SessionOutcome {
    status: String,
    verified_peer: Option<String>,
}

impl SessionOutcome {
    fn stopped(status: String) -> Self {
        Self {
            status,
            verified_peer: None,
        }
    }
}

/// The comparison this phone's user is asked to make, and where their answer goes.
struct PendingVerification {
    screen_text: String,
    answer: Sender<bool>,
}

static PENDING_VERIFICATION: Mutex<Option<PendingVerification>> = Mutex::new(None);
static LAST_VERIFIED_PEER: Mutex<Option<String>> = Mutex::new(None);

/// One person's answer to the safety-code comparison.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Answer {
    Confirmed,
    Rejected,
    /// The time to compare ran out without an answer.
    Unanswered,
}

impl Answer {
    /// The fixed marker a phone sends inside MLS for this answer.
    fn marker(self) -> &'static [u8] {
        match self {
            Answer::Confirmed => CODE_CONFIRMED,
            Answer::Rejected => CODE_REJECTED,
            Answer::Unanswered => CODE_UNANSWERED,
        }
    }

    fn from_marker(data: &[u8]) -> Option<Self> {
        [Answer::Confirmed, Answer::Rejected, Answer::Unanswered]
            .into_iter()
            .find(|answer| answer.marker() == data)
    }
}

/// Where this phone's person's answer arrives, and how long they have to give it. Dropping it
/// withdraws an on-screen comparison, so a late tap is refused.
struct LocalAnswer {
    answers: Receiver<bool>,
    time_limit: Duration,
    on_screen: bool,
}

impl LocalAnswer {
    /// The person's answer, once they have given it.
    fn given(&self) -> Option<Answer> {
        match self.answers.try_recv() {
            Ok(true) => Some(Answer::Confirmed),
            Ok(false) => Some(Answer::Rejected),
            Err(TryRecvError::Empty) => None,
            // Nobody can answer any more, so the comparison stays unanswered.
            Err(TryRecvError::Disconnected) => Some(Answer::Unanswered),
        }
    }
}

impl Drop for LocalAnswer {
    fn drop(&mut self) {
        if !self.on_screen {
            return;
        }
        if let Ok(mut pending) = PENDING_VERIFICATION.lock() {
            *pending = None;
        }
    }
}

/// The comparison waiting for this phone's user as `code|status|device`, if any.
pub fn lan_safety_prompt() -> Option<String> {
    let pending = PENDING_VERIFICATION.lock().ok()?;
    pending
        .as_ref()
        .map(|verification| verification.screen_text.clone())
}

/// Pass this phone's user answer to the waiting LAN test; false if no comparison is waiting.
pub fn submit_lan_safety_decision(codes_match: bool) -> bool {
    let Ok(mut pending) = PENDING_VERIFICATION.lock() else {
        return false;
    };
    pending
        .take()
        .is_some_and(|verification| verification.answer.send(codes_match).is_ok())
}

/// The peer verified by the last PASS on this phone, as `<device ID> <public key hex>`; read once.
pub fn take_lan_verified_peer() -> Option<String> {
    LAST_VERIFIED_PEER.lock().ok()?.take()
}

fn set_verified_peer(peer: Option<String>) {
    if let Ok(mut last) = LAST_VERIFIED_PEER.lock() {
        *last = peer;
    }
}

/// Something this phone's person asked the typed test to do.
#[derive(Debug, PartialEq, Eq)]
enum ChatCommand {
    /// Send this phone's typed message, numbered by this phone.
    Send(u32, String),
    /// End the typed test for both phones.
    End,
}

/// Something the typed test tells this phone's screen.
#[derive(Debug, PartialEq, Eq)]
enum ChatEvent {
    /// Both people confirmed the code and the fixed exchange passed; typing is open.
    Open { verified_peer: String },
    /// The other phone confirmed it received this phone's message with this number.
    Delivered(u32),
    /// The other phone's typed message and its number.
    Received(u32, String),
    /// The typed test ended; the text is the report line.
    Ended(String),
}

impl ChatEvent {
    /// The form the test APK's screen reads; typed text is always the last field.
    fn screen_text(&self) -> String {
        match self {
            ChatEvent::Open { .. } => format!("OPEN|{LAN_PASS_STATUS}"),
            ChatEvent::Delivered(number) => format!("DELIVERED|{number}"),
            ChatEvent::Received(number, text) => format!("RECEIVED|{number}|{text}"),
            ChatEvent::Ended(summary) => format!("ENDED|{summary}"),
        }
    }
}

/// This phone's person in the typed test: their requests in, what happened out.
trait ChatSide {
    fn next_command(&mut self) -> Option<ChatCommand>;
    fn report(&mut self, event: ChatEvent);
}

/// Typed messages and end requests from the test APK's screen, waiting for the session.
struct ChatOutbox {
    open: bool,
    next_number: u32,
    commands: VecDeque<ChatCommand>,
    /// The running session's limits for this phone's typed messages.
    max_text_bytes: usize,
    max_messages: u32,
}

static CHAT_OUTBOX: Mutex<ChatOutbox> = Mutex::new(ChatOutbox {
    open: false,
    next_number: 1,
    commands: VecDeque::new(),
    max_text_bytes: MAX_TYPED_MESSAGE_BYTES,
    max_messages: MAX_TYPED_MESSAGES,
});
static CHAT_EVENTS: Mutex<VecDeque<String>> = Mutex::new(VecDeque::new());

/// The test APK's screen, reached through the queues its JNI calls use.
struct ScreenChat;

impl ChatSide for ScreenChat {
    fn next_command(&mut self) -> Option<ChatCommand> {
        CHAT_OUTBOX.lock().ok()?.commands.pop_front()
    }

    fn report(&mut self, event: ChatEvent) {
        match &event {
            ChatEvent::Open { verified_peer } => {
                // The app remembers the verified phone as soon as typing opens.
                set_verified_peer(Some(verified_peer.clone()));
                set_chat_open(true);
            }
            ChatEvent::Ended(_) => set_chat_open(false),
            ChatEvent::Delivered(_) | ChatEvent::Received(..) => {}
        }
        if let Ok(mut events) = CHAT_EVENTS.lock() {
            events.push_back(event.screen_text());
        }
    }
}

fn set_chat_open(open: bool) {
    if let Ok(mut outbox) = CHAT_OUTBOX.lock() {
        outbox.open = open;
        outbox.next_number = 1;
        outbox.commands.clear();
    }
}

/// Forget any earlier typed test before a new LAN session starts, and use this app's limits.
fn reset_screen_chat(profile: &LanProfile) {
    set_chat_open(false);
    if let Ok(mut outbox) = CHAT_OUTBOX.lock() {
        outbox.max_text_bytes = profile.max_text_bytes;
        outbox.max_messages = profile.max_messages;
    }
    if let Ok(mut events) = CHAT_EVENTS.lock() {
        events.clear();
    }
}

fn validate_typed_text(text: &str, max_bytes: usize) -> Result<(), String> {
    if text.trim().is_empty() {
        return Err("Type a message first.".into());
    }
    if text.len() > max_bytes {
        return Err(format!(
            "A typed test message can be at most {max_bytes} bytes."
        ));
    }
    Ok(())
}

/// Queue a typed test message from this phone's screen; returns its number on this phone.
pub fn lan_chat_send(text: &str) -> Result<u32, String> {
    let mut outbox = CHAT_OUTBOX
        .lock()
        .map_err(|_| "The typed test is unavailable.".to_owned())?;
    validate_typed_text(text, outbox.max_text_bytes)?;
    if !outbox.open {
        return Err(
            "No typed test is open; both phones must confirm the safety code first.".into(),
        );
    }
    if outbox.next_number > outbox.max_messages {
        return Err(format!(
            "This test allows {} typed messages per phone; end it and start a new test.",
            outbox.max_messages
        ));
    }
    let number = outbox.next_number;
    outbox.next_number += 1;
    outbox
        .commands
        .push_back(ChatCommand::Send(number, text.to_owned()));
    Ok(number)
}

/// Ask the open typed test to end for both phones; false if none is open.
pub fn lan_chat_end() -> bool {
    let Ok(mut outbox) = CHAT_OUTBOX.lock() else {
        return false;
    };
    if outbox.open {
        outbox.commands.push_back(ChatCommand::End);
    }
    outbox.open
}

/// The next typed-test event for the screen: `OPEN|status`, `DELIVERED|number`,
/// `RECEIVED|number|text` or `ENDED|summary`.
pub fn lan_chat_poll() -> Option<String> {
    CHAT_EVENTS.lock().ok()?.pop_front()
}

/// Offer the comparison to the test APK's screen; this phone's person's answer arrives through
/// the result, which withdraws the comparison when dropped.
fn ask_local_user(prompt: &SafetyPrompt) -> Result<LocalAnswer, String> {
    let (answer, answers) = mpsc::channel();
    {
        let mut pending = PENDING_VERIFICATION
            .lock()
            .map_err(|_| "The safety-code prompt is unavailable.".to_owned())?;
        *pending = Some(PendingVerification {
            screen_text: prompt.screen_text(),
            answer,
        });
    }
    Ok(LocalAnswer {
        answers,
        time_limit: VERIFY_TIMEOUT,
        on_screen: true,
    })
}

fn validate_device_id(device_id: &str) -> Result<(), String> {
    let well_formed = device_id.len() == DEVICE_ID_LENGTH
        && device_id
            .bytes()
            .all(|byte| byte.is_ascii_digit() || (b'a'..=b'f').contains(&byte));
    if well_formed {
        Ok(())
    } else {
        Err("The test device ID must be 16 lowercase hexadecimal characters.".into())
    }
}

fn device_label(device_id: &str) -> String {
    format!("{DEVICE_LABEL_PREFIX}{device_id}")
}

fn to_hex(bytes: &[u8]) -> String {
    const DIGITS: &[u8; 16] = b"0123456789abcdef";
    let mut text = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        text.push(char::from(DIGITS[usize::from(byte >> 4)]));
        text.push(char::from(DIGITS[usize::from(byte & 0x0f)]));
    }
    text
}

fn from_hex(text: &str, what: &str) -> Result<Vec<u8>, String> {
    let digits = text.as_bytes();
    if !digits.len().is_multiple_of(2) || !digits.iter().all(u8::is_ascii_hexdigit) {
        return Err(format!("The {what} is not valid hexadecimal."));
    }
    Ok(digits
        .chunks(2)
        .map(|pair| (hex_value(pair[0]) << 4) | hex_value(pair[1]))
        .collect())
}

fn hex_value(digit: u8) -> u8 {
    match digit {
        b'0'..=b'9' => digit - b'0',
        b'a'..=b'f' => digit - b'a' + 10,
        b'A'..=b'F' => digit - b'A' + 10,
        _ => 0,
    }
}

/// Create a persistent test identity for this install; the app stores the text Keystore-wrapped.
///
/// The text is `v1:<device ID>:<secret key hex>:<public key hex>`. A reset keeps the device ID, so
/// phones that remembered the old key see a key-change warning.
pub fn generate_lan_identity(device_id: &str) -> Result<String, String> {
    validate_device_id(device_id)?;
    let (signing_identity, secret_key) =
        test_signing_material(&test_crypto_provider(), &device_label(device_id));
    Ok(format!(
        "{IDENTITY_FORMAT}:{device_id}:{}:{}",
        to_hex(secret_key.as_bytes()),
        to_hex(signing_identity.signature_key.as_bytes())
    ))
}

fn parse_lan_identity(text: &str) -> Result<LanIdentity, String> {
    let mut fields = text.trim().split(':');
    let (Some(IDENTITY_FORMAT), Some(device_id), Some(secret_hex), Some(public_hex), None) = (
        fields.next(),
        fields.next(),
        fields.next(),
        fields.next(),
        fields.next(),
    ) else {
        return Err("This phone's stored test identity is malformed; reset its test key.".into());
    };
    validate_device_id(device_id)?;
    let secret = from_hex(secret_hex, "stored test identity key")?;
    let public = from_hex(public_hex, "stored test public key")?;
    if secret.is_empty() || public.is_empty() {
        return Err("This phone's stored test identity is incomplete; reset its test key.".into());
    }
    Ok(LanIdentity {
        device_id: device_id.to_owned(),
        secret_key: SignatureSecretKey::new(secret),
        public_key: SignaturePublicKey::new(public),
    })
}

fn identity_client(identity: LanIdentity) -> Result<Client<impl MlsConfig>, String> {
    let credential = BasicCredential::new(device_label(&identity.device_id).into_bytes());
    let signing_identity = SigningIdentity::new(credential.into_credential(), identity.public_key);
    test_peer_client(
        test_crypto_provider(),
        signing_identity,
        identity.secret_key,
    )
    .map_err(|error| format!("Could not create the test MLS client: {error}"))
}

/// The other member's device ID and signature key, as this phone's MLS group records them.
fn peer_identity<C: MlsConfig>(
    group: &mls_rs::group::Group<C>,
) -> Result<(String, Vec<u8>), String> {
    let own_index = group.current_member_index();
    let peer = group
        .roster()
        .members()
        .into_iter()
        .find(|member| member.index != own_index)
        .ok_or_else(|| "The MLS group has no other member.".to_owned())?;
    let label = peer
        .signing_identity
        .credential
        .as_basic()
        .map(|credential| credential.identifier().to_vec())
        .ok_or_else(|| "The other member does not use a basic test credential.".to_owned())?;
    let device_id = std::str::from_utf8(&label)
        .ok()
        .and_then(|label| label.strip_prefix(DEVICE_LABEL_PREFIX))
        .filter(|device_id| validate_device_id(device_id).is_ok())
        .ok_or_else(|| "The other member's test device ID is malformed.".to_owned())?
        .to_owned();
    Ok((
        device_id,
        peer.signing_identity.signature_key.as_bytes().to_vec(),
    ))
}

/// Compare the peer with this phone's remembered peers, one `<device ID> <key hex>` per line.
fn classify_peer(device_id: &str, public_key: &[u8], pins: &str) -> PeerStatus {
    let key_hex = to_hex(public_key);
    let mut status = PeerStatus::New;
    for line in pins.lines() {
        let mut fields = line.split_whitespace();
        let (Some(pinned_device), Some(pinned_key), None) =
            (fields.next(), fields.next(), fields.next())
        else {
            continue;
        };
        if pinned_device != device_id {
            continue;
        }
        if pinned_key == key_hex {
            return PeerStatus::Known;
        }
        status = PeerStatus::KeyChanged;
    }
    status
}

/// Build the comparison for this phone's group, and the pin to keep if the session passes.
fn safety_prompt<C: MlsConfig>(
    group: &mls_rs::group::Group<C>,
    pins: &str,
) -> Result<(SafetyPrompt, String), String> {
    let (peer_device, peer_key) = peer_identity(group)?;
    let pin = format!("{peer_device} {}", to_hex(&peer_key));
    let prompt = SafetyPrompt {
        code: group_safety_code(group)?,
        peer_status: classify_peer(&peer_device, &peer_key, pins),
        peer_device,
    };
    Ok((prompt, pin))
}

/// Format an RFC 9420 epoch authenticator as four groups of five digits for people to compare.
///
/// Each group is a 40-bit big-endian chunk reduced modulo 100000 (about 66 bits in total), as in
/// libsignal's numeric fingerprints. A relaying device would have to make two separate groups agree
/// on every digit before the joining phone stops waiting for its Welcome.
fn safety_code_from_authenticator(authenticator: &[u8]) -> Result<String, String> {
    let needed = SAFETY_CODE_GROUPS * SAFETY_CODE_CHUNK_BYTES;
    let bytes = authenticator
        .get(..needed)
        .ok_or_else(|| "The MLS epoch authenticator is too short for a safety code.".to_owned())?;
    let groups: Vec<String> = bytes
        .chunks(SAFETY_CODE_CHUNK_BYTES)
        .map(|chunk| {
            let value = chunk
                .iter()
                .fold(0u64, |value, byte| (value << 8) | u64::from(*byte));
            let digits = value % 100_000;
            format!("{digits:05}")
        })
        .collect();
    Ok(groups.join(" "))
}

fn group_safety_code<C: MlsConfig>(group: &mls_rs::group::Group<C>) -> Result<String, String> {
    let authenticator = group
        .epoch_authenticator()
        .map_err(|error| format!("Could not read the MLS epoch authenticator: {error}"))?;
    safety_code_from_authenticator(authenticator.as_bytes())
}

fn test_peer_client(
    crypto_provider: AwsLcCryptoProvider,
    signing_identity: SigningIdentity,
    secret_key: SignatureSecretKey,
) -> Result<Client<impl MlsConfig>, mls_rs::error::MlsError> {
    // mls-rs documents BasicIdentityProvider as always-valid for BasicCredential and
    // recommends it only for tests. The safety-code comparison is this test's identity check.
    Ok(Client::builder()
        .identity_provider(BasicIdentityProvider::new())
        .crypto_provider(crypto_provider)
        .signing_identity(signing_identity, secret_key, TEST_CIPHERSUITE)
        .build())
}

fn require_private_ipv4(value: &str) -> Result<Ipv4Addr, String> {
    let address = value
        .parse::<Ipv4Addr>()
        .map_err(|_| "Use a dotted-decimal private Wi-Fi IPv4 address.".to_owned())?;
    if !address.is_private() {
        return Err("For this test, the peer address must be a private LAN IPv4 address.".into());
    }
    Ok(address)
}

fn validate_invite_token(token: &str) -> Result<(), String> {
    if token.len() != 32 || !token.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return Err("The one-time invite token is malformed.".into());
    }
    Ok(())
}

/// Stop a host that is still waiting for the other phone; false if no host is waiting.
pub fn lan_cancel_host_wait() -> bool {
    if HOST_WAITING.load(Ordering::SeqCst) {
        HOST_CANCELLED.store(true, Ordering::SeqCst);
        true
    } else {
        false
    }
}

fn accept_with_timeout(
    listener: &TcpListener,
    wait: Duration,
    cancelled: &AtomicBool,
) -> Result<TcpStream, String> {
    let deadline = Instant::now() + wait;
    loop {
        if cancelled.load(Ordering::SeqCst) {
            return Err("Stopped waiting for the other phone to join.".into());
        }
        match listener.accept() {
            Ok((stream, _peer)) => return Ok(stream),
            Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                if Instant::now() >= deadline {
                    return Err("Timed out waiting for a LAN peer to join the test invite.".into());
                }
                thread::sleep(Duration::from_millis(100));
            }
            Err(error) if error.kind() == std::io::ErrorKind::Interrupted => continue,
            Err(error) => return Err(format!("Could not accept a LAN peer: {error}")),
        }
    }
}

/// The connection between the two phones: a TCP socket on the same Wi-Fi, or, through the
/// Internet relay, the session's end of a local socket pair whose other end the app copies to and
/// from the relay's WebSocket. The session uses only these operations, so it runs the same way
/// over either.
trait PeerStream: Read + Write {
    /// Apply the session's usual read and write timeouts (TCP also sends small frames at once).
    fn configure(&self) -> std::io::Result<()>;
    /// How long a read may wait before it reports a timeout.
    fn set_wait(&self, timeout: Duration) -> std::io::Result<()>;
    /// Send nothing more: the other side reads what was sent, then the end of the stream.
    fn stop_sending(&self) -> std::io::Result<()>;
}

impl PeerStream for TcpStream {
    fn configure(&self) -> std::io::Result<()> {
        self.set_read_timeout(Some(IO_TIMEOUT))?;
        self.set_write_timeout(Some(IO_TIMEOUT))?;
        self.set_nodelay(true)
    }

    fn set_wait(&self, timeout: Duration) -> std::io::Result<()> {
        self.set_read_timeout(Some(timeout))
    }

    fn stop_sending(&self) -> std::io::Result<()> {
        self.shutdown(Shutdown::Write)
    }
}

#[cfg(unix)]
impl PeerStream for UnixStream {
    fn configure(&self) -> std::io::Result<()> {
        self.set_read_timeout(Some(IO_TIMEOUT))?;
        self.set_write_timeout(Some(IO_TIMEOUT))
    }

    fn set_wait(&self, timeout: Duration) -> std::io::Result<()> {
        self.set_read_timeout(Some(timeout))
    }

    fn stop_sending(&self) -> std::io::Result<()> {
        self.shutdown(Shutdown::Write)
    }
}

fn configure_stream(stream: &impl PeerStream) -> Result<(), String> {
    stream
        .configure()
        .map_err(|error| format!("Could not configure the LAN test socket: {error}"))
}

fn set_read_timeout(stream: &impl PeerStream, timeout: Duration) -> Result<(), String> {
    stream
        .set_wait(timeout)
        .map_err(|error| format!("Could not configure the LAN test socket: {error}"))
}

fn send_frame(stream: &mut impl Write, bytes: &[u8]) -> Result<(), String> {
    if bytes.len() > MAX_FRAME_BYTES {
        return Err("Refusing an oversized test frame.".into());
    }
    let length =
        u32::try_from(bytes.len()).map_err(|_| "Test frame length overflowed.".to_owned())?;
    stream
        .write_all(&length.to_be_bytes())
        .and_then(|_| stream.write_all(bytes))
        .and_then(|_| stream.flush())
        .map_err(|error| connection_failure("send the LAN test frame", &error))
}

fn receive_frame(stream: &mut impl Read) -> Result<Vec<u8>, String> {
    let mut length_bytes = [0u8; 4];
    stream
        .read_exact(&mut length_bytes)
        .map_err(|error| connection_failure("read the LAN test frame length", &error))?;
    let length = u32::from_be_bytes(length_bytes) as usize;
    if length > MAX_FRAME_BYTES {
        return Err("Peer sent an oversized test frame.".into());
    }
    let mut bytes = vec![0u8; length];
    stream
        .read_exact(&mut bytes)
        .map_err(|error| connection_failure("read the complete LAN test frame", &error))?;
    Ok(bytes)
}

/// Describe a socket failure; a closed connection also gets the keep-the-app-open hint.
fn connection_failure(action: &str, error: &std::io::Error) -> String {
    match error.kind() {
        ErrorKind::ConnectionAborted
        | ErrorKind::ConnectionReset
        | ErrorKind::BrokenPipe
        | ErrorKind::NotConnected
        | ErrorKind::UnexpectedEof => {
            format!("Could not {action}: {error}. The connection was closed. {KEEP_APP_OPEN_HINT}")
        }
        _ => format!("Could not {action}: {error}"),
    }
}

/// A read that timed out or was interrupted; the typed test simply checks again.
fn is_wait(error: &std::io::Error) -> bool {
    matches!(
        error.kind(),
        ErrorKind::WouldBlock | ErrorKind::TimedOut | ErrorKind::Interrupted
    )
}

/// The joining phone's first frame: this app's protocol tag, a space, and the invite token.
fn guest_hello(profile: &LanProfile, token: &str) -> String {
    format!("{} {token}", profile.protocol_tag)
}

/// Check the joining phone's first frame, naming an older or different test APK clearly.
fn check_guest_hello(profile: &LanProfile, hello: &[u8], token: &str) -> Result<(), String> {
    if hello == token.as_bytes() {
        return Err("The other phone is running an older test APK. Install the same test APK version on both phones.".into());
    }
    match std::str::from_utf8(hello)
        .ok()
        .and_then(|text| text.split_once(' '))
    {
        Some((tag, supplied)) if tag == profile.protocol_tag && supplied == token => Ok(()),
        Some((tag, _))
            if tag.starts_with(profile.protocol_family) && tag != profile.protocol_tag =>
        {
            Err(
            "The other phone is running a different test APK version. Install the same test APK version on both phones."
                .into(),
        )
        }
        _ => Err("The invite does not match the active host session.".into()),
    }
}

fn parse_mls_message(bytes: &[u8], context: &str) -> Result<MlsMessage, String> {
    MlsMessage::from_bytes(bytes).map_err(|error| format!("Could not parse {context}: {error}"))
}

fn verify_application_message(
    message: ReceivedMessage,
    expected: &[u8],
    context: &str,
) -> Result<(), String> {
    match message {
        ReceivedMessage::ApplicationMessage(application) if application.data() == expected => {
            Ok(())
        }
        ReceivedMessage::ApplicationMessage(_) => {
            Err(format!("{context} did not match the fixed test marker."))
        }
        _ => Err(format!("{context} was not an MLS application message.")),
    }
}

fn encrypt_test_message<C: MlsConfig>(
    group: &mut mls_rs::group::Group<C>,
    plaintext: &[u8],
    context: &str,
) -> Result<Vec<u8>, String> {
    let message = group
        .encrypt_application_message(plaintext, Vec::new())
        .map_err(|error| format!("Could not encrypt {context}: {error}"))?;
    let wire = message
        .to_bytes()
        .map_err(|error| format!("Could not serialize {context}: {error}"))?;
    if wire
        .windows(plaintext.len())
        .any(|window| window == plaintext)
    {
        return Err(format!(
            "The fixed plaintext marker appeared in {context} wire bytes."
        ));
    }
    Ok(wire)
}

/// Reads one length-prefixed frame across short socket reads without reading past its end, so
/// the session can go on reading later frames from the stream directly.
#[derive(Default)]
struct PartialFrame {
    length: [u8; 4],
    length_read: usize,
    body: Vec<u8>,
    body_read: usize,
}

impl PartialFrame {
    /// Read what arrives within the stream's read timeout; the frame once it is complete.
    fn poll(&mut self, stream: &mut impl Read) -> Result<Option<Vec<u8>>, String> {
        loop {
            if self.length_read == self.length.len() && self.body_read == self.body.len() {
                return Ok(Some(std::mem::take(self).body));
            }
            let unread = if self.length_read < self.length.len() {
                &mut self.length[self.length_read..]
            } else {
                &mut self.body[self.body_read..]
            };
            let read = match stream.read(unread) {
                Ok(0) => {
                    return Err(format!(
                        "The other phone closed the connection during the safety-code comparison. {KEEP_APP_OPEN_HINT}"
                    ))
                }
                Ok(read) => read,
                Err(error) if is_wait(&error) => return Ok(None),
                Err(error) => {
                    return Err(connection_failure(
                        "read the other phone's safety-code answer",
                        &error,
                    ))
                }
            };
            if self.length_read < self.length.len() {
                self.length_read += read;
                if self.length_read == self.length.len() {
                    let length = u32::from_be_bytes(self.length) as usize;
                    if length > MAX_SHORT_FRAME_BYTES {
                        return Err("The other phone sent an oversized safety-code answer.".into());
                    }
                    self.body = vec![0; length];
                }
            } else {
                self.body_read += read;
            }
        }
    }
}

/// Decrypt the other phone's answer to the comparison.
fn read_peer_answer<C: MlsConfig>(
    group: &mut mls_rs::group::Group<C>,
    frame: &[u8],
) -> Result<Answer, String> {
    let message = parse_mls_message(frame, "peer safety-code answer")?;
    let received = group
        .process_incoming_message(message)
        .map_err(|error| format!("Rejected the peer's MLS safety-code answer: {error}"))?;
    match received {
        ReceivedMessage::ApplicationMessage(application) => Answer::from_marker(application.data()),
        _ => None,
    }
    .ok_or_else(|| "The peer's safety-code answer was not recognized.".to_owned())
}

/// Ask this phone's person to compare the safety code while watching for the other phone's
/// answer, and exchange both answers inside MLS.
///
/// Returns a STOPPED status unless both people confirmed. If the other phone reports different
/// codes (or no answer) first, this phone stops at once and withdraws its own question. A phone
/// whose person does not confirm sends that answer and closes after a short linger, without
/// waiting for the other person; a phone whose person confirms waits for the other answer.
fn verify_safety_code<C, D, S>(
    stream: &mut S,
    group: &mut mls_rs::group::Group<C>,
    prompt: &SafetyPrompt,
    decide: D,
) -> Result<Option<String>, String>
where
    C: MlsConfig,
    D: FnOnce(&SafetyPrompt) -> Result<LocalAnswer, String>,
    S: PeerStream,
{
    let local = decide(prompt)?;
    let deadline = Instant::now() + local.time_limit;
    set_read_timeout(stream, SCREEN_POLL)?;
    let mut incoming = PartialFrame::default();
    let mut peer = None;
    let mine = loop {
        if let Some(answer) = local.given() {
            break answer;
        }
        if Instant::now() >= deadline {
            break Answer::Unanswered;
        }
        let Some(frame) = incoming.poll(stream)? else {
            continue;
        };
        if peer.is_some() {
            return Err("The other phone sent a second safety-code answer.".into());
        }
        match read_peer_answer(group, &frame)? {
            Answer::Confirmed => peer = Some(Answer::Confirmed),
            Answer::Rejected => return Ok(Some(PEER_REJECTED_STATUS.into())),
            Answer::Unanswered => return Ok(Some(PEER_UNANSWERED_STATUS.into())),
        }
    };
    drop(local);

    let answer_wire = encrypt_test_message(group, mine.marker(), "safety-code answer")?;
    send_frame(stream, &answer_wire)?;
    let stopped = match mine {
        Answer::Confirmed => None,
        Answer::Rejected => Some(LOCAL_REJECTED_STATUS),
        Answer::Unanswered => Some(LOCAL_UNANSWERED_STATUS),
    };
    if let Some(status) = stopped {
        linger_before_close(stream);
        return Ok(Some(status.into()));
    }

    let peer = match peer {
        Some(answer) => answer,
        None => loop {
            if let Some(frame) = incoming.poll(stream)? {
                break read_peer_answer(group, &frame)?;
            }
            if Instant::now() >= deadline + IO_TIMEOUT {
                return Err("The other phone did not send its safety-code answer in time.".into());
            }
        },
    };
    set_read_timeout(stream, IO_TIMEOUT)?;
    Ok(match peer {
        Answer::Confirmed => None,
        Answer::Rejected => Some(PEER_REJECTED_STATUS.into()),
        Answer::Unanswered => Some(PEER_UNANSWERED_STATUS.into()),
    })
}

/// One typed-test payload inside an MLS application message: a kind byte, the message number
/// (big-endian), then the UTF-8 text for a typed message.
#[derive(Debug, PartialEq, Eq)]
enum ChatPayload {
    Text(u32, String),
    Ack(u32),
    End,
}

impl ChatPayload {
    fn to_bytes(&self) -> Vec<u8> {
        let (kind, number, text) = match self {
            ChatPayload::Text(number, text) => (CHAT_TEXT, *number, text.as_str()),
            ChatPayload::Ack(number) => (CHAT_ACK, *number, ""),
            ChatPayload::End => (CHAT_END, 0, ""),
        };
        let mut bytes = Vec::with_capacity(5 + text.len());
        bytes.push(kind);
        bytes.extend_from_slice(&number.to_be_bytes());
        bytes.extend_from_slice(text.as_bytes());
        bytes
    }

    fn parse(bytes: &[u8], max_text_bytes: usize) -> Result<Self, String> {
        let malformed = || "The other phone sent a malformed typed-test message.".to_owned();
        let [kind, a, b, c, d, body @ ..] = bytes else {
            return Err(malformed());
        };
        let number = u32::from_be_bytes([*a, *b, *c, *d]);
        match *kind {
            CHAT_TEXT => {
                let text = std::str::from_utf8(body).map_err(|_| malformed())?;
                validate_typed_text(text, max_text_bytes).map_err(|_| malformed())?;
                Ok(ChatPayload::Text(number, text.to_owned()))
            }
            CHAT_ACK if body.is_empty() => Ok(ChatPayload::Ack(number)),
            CHAT_END if body.is_empty() && number == 0 => Ok(ChatPayload::End),
            _ => Err(malformed()),
        }
    }
}

fn encrypt_chat<C: MlsConfig>(
    group: &mut mls_rs::group::Group<C>,
    payload: &ChatPayload,
) -> Result<Vec<u8>, String> {
    let message = group
        .encrypt_application_message(&payload.to_bytes(), Vec::new())
        .map_err(|error| format!("Could not encrypt a typed test message: {error}."))?;
    let wire = message
        .to_bytes()
        .map_err(|error| format!("Could not serialize a typed test message: {error}."))?;
    if let ChatPayload::Text(_, text) = payload {
        // A cheap guard against an unencrypted transport; very short texts could match by chance.
        if text.len() >= 16
            && wire
                .windows(text.len())
                .any(|window| window == text.as_bytes())
        {
            return Err(
                "The typed text appeared in the MLS wire bytes, so it was not sent.".into(),
            );
        }
    }
    Ok(wire)
}

/// Collects length-prefixed frames from a socket read with a short timeout, keeping any partial
/// frame for the next read.
struct FrameReader {
    buffered: Vec<u8>,
    max_frame_bytes: usize,
}

impl FrameReader {
    fn new(max_frame_bytes: usize) -> Self {
        Self {
            buffered: Vec::new(),
            max_frame_bytes,
        }
    }

    /// The next complete frame, reading the socket at most once; `None` if none has arrived yet.
    fn next_frame(&mut self, stream: &mut impl Read) -> Result<Option<Vec<u8>>, String> {
        if let Some(frame) = self.take_frame()? {
            return Ok(Some(frame));
        }
        let mut chunk = [0u8; 16 * 1024];
        match stream.read(&mut chunk) {
            Ok(0) => Err(format!(
                "The other phone closed the connection without ending the typed test. {KEEP_APP_OPEN_HINT}"
            )),
            Ok(read) => {
                self.buffered.extend_from_slice(&chunk[..read]);
                self.take_frame()
            }
            Err(error) if is_wait(&error) => Ok(None),
            Err(error) => Err(connection_failure("read a typed test message", &error)),
        }
    }

    fn take_frame(&mut self) -> Result<Option<Vec<u8>>, String> {
        let [a, b, c, d, ..] = self.buffered.as_slice() else {
            return Ok(None);
        };
        let length = u32::from_be_bytes([*a, *b, *c, *d]) as usize;
        if length > self.max_frame_bytes {
            return Err("The other phone sent an oversized typed-test frame.".into());
        }
        if self.buffered.len() < 4 + length {
            return Ok(None);
        }
        let frame = self.buffered[4..4 + length].to_vec();
        self.buffered.drain(..4 + length);
        Ok(Some(frame))
    }
}

/// How the typed test ended.
enum ChatEnd {
    ThisPhone,
    OtherPhone,
    /// The session's time limit passed.
    TimeLimit(Duration),
    Stopped(String),
}

/// What the typed test carried, for the report: counts only, never the text.
#[derive(Default)]
struct ChatTally {
    sent: u32,
    delivered: u32,
    received: u32,
}

/// Run the typed test over the verified MLS group until a person ends it, the time limit
/// passes, or the connection fails; returns the report line.
fn run_typed_test<C, S, T>(
    profile: &LanProfile,
    stream: &mut T,
    group: &mut mls_rs::group::Group<C>,
    chat: &mut S,
    verified_peer: String,
) -> String
where
    C: MlsConfig,
    S: ChatSide,
    T: PeerStream,
{
    chat.report(ChatEvent::Open { verified_peer });
    let started = Instant::now();
    let mut tally = ChatTally::default();
    let end = chat_loop(profile, stream, group, chat, started, &mut tally)
        .unwrap_or_else(ChatEnd::Stopped);
    let summary = chat_summary(&end, started.elapsed(), &tally);
    chat.report(ChatEvent::Ended(summary.clone()));
    summary
}

fn chat_loop<C, S, T>(
    profile: &LanProfile,
    stream: &mut T,
    group: &mut mls_rs::group::Group<C>,
    chat: &mut S,
    started: Instant,
    tally: &mut ChatTally,
) -> Result<ChatEnd, String>
where
    C: MlsConfig,
    S: ChatSide,
    T: PeerStream,
{
    set_read_timeout(stream, SCREEN_POLL)?;
    let mut frames = FrameReader::new(profile.max_chat_frame_bytes);
    loop {
        while let Some(command) = chat.next_command() {
            match command {
                ChatCommand::Send(number, text) => {
                    let wire = encrypt_chat(group, &ChatPayload::Text(number, text))?;
                    send_frame(stream, &wire)?;
                    tally.sent += 1;
                }
                ChatCommand::End => {
                    end_for_both(stream, group);
                    return Ok(ChatEnd::ThisPhone);
                }
            }
        }
        if started.elapsed() >= profile.chat_limit {
            end_for_both(stream, group);
            return Ok(ChatEnd::TimeLimit(profile.chat_limit));
        }
        let Some(frame) = frames.next_frame(stream)? else {
            continue;
        };
        let message = parse_mls_message(&frame, "the other phone's typed test message")?;
        let received = group.process_incoming_message(message).map_err(|error| {
            format!("Rejected the other phone's MLS typed test message: {error}.")
        })?;
        let ReceivedMessage::ApplicationMessage(application) = received else {
            return Err(
                "The other phone sent something other than an MLS application message.".into(),
            );
        };
        match ChatPayload::parse(application.data(), profile.max_text_bytes)? {
            ChatPayload::Text(number, text) => {
                tally.received += 1;
                chat.report(ChatEvent::Received(number, text));
                let ack = encrypt_chat(group, &ChatPayload::Ack(number))?;
                send_frame(stream, &ack)?;
            }
            ChatPayload::Ack(number) => {
                tally.delivered += 1;
                chat.report(ChatEvent::Delivered(number));
            }
            ChatPayload::End => return Ok(ChatEnd::OtherPhone),
        }
    }
}

/// Tell the other phone the typed test is over, then let it read that before closing.
fn end_for_both<C: MlsConfig>(stream: &mut impl PeerStream, group: &mut mls_rs::group::Group<C>) {
    let sent = encrypt_chat(group, &ChatPayload::End).and_then(|wire| send_frame(stream, &wire));
    if sent.is_ok() {
        linger_before_close(stream);
    }
}

/// After this phone's last message, stop sending and give the other phone a moment to read
/// everything before the connection closes, discarding anything that still arrives.
fn linger_before_close(stream: &mut impl PeerStream) {
    let _ = stream.stop_sending();
    let _ = stream.set_wait(SCREEN_POLL);
    let deadline = Instant::now() + END_LINGER;
    let mut discarded = [0u8; 512];
    while Instant::now() < deadline {
        match stream.read(&mut discarded) {
            Ok(0) => break,
            Ok(_) => {}
            Err(error) if is_wait(&error) => {}
            Err(_) => break,
        }
    }
}

fn chat_summary(end: &ChatEnd, elapsed: Duration, tally: &ChatTally) -> String {
    let how = match end {
        ChatEnd::ThisPhone => "Ended on this phone.".to_owned(),
        ChatEnd::OtherPhone => "Ended on the other phone.".to_owned(),
        ChatEnd::TimeLimit(limit) => {
            format!("Ended at the {}-minute test limit.", limit.as_secs() / 60)
        }
        ChatEnd::Stopped(reason) if reason.ends_with('.') => format!("Stopped. {reason}"),
        ChatEnd::Stopped(reason) => format!("Stopped. {reason}."),
    };
    format!(
        "{how} Sent {} ({} delivered), received {}, in {}.",
        tally.sent,
        tally.delivered,
        tally.received,
        format_elapsed(elapsed)
    )
}

fn format_elapsed(elapsed: Duration) -> String {
    let seconds = elapsed.as_secs();
    if seconds < 60 {
        format!("{seconds} s")
    } else {
        format!("{} min {} s", seconds / 60, seconds % 60)
    }
}

fn host_session<D, S, T>(
    profile: &LanProfile,
    mut stream: T,
    token: &str,
    identity: LanIdentity,
    pins: &str,
    decide: D,
    mut chat: S,
) -> Result<SessionOutcome, String>
where
    D: FnOnce(&SafetyPrompt) -> Result<LocalAnswer, String>,
    S: ChatSide,
    T: PeerStream,
{
    configure_stream(&stream)?;

    let hello = receive_frame(&mut stream)?;
    check_guest_hello(profile, &hello, token)?;

    let guest_key_package_wire = receive_frame(&mut stream)?;
    let guest_key_package = parse_mls_message(&guest_key_package_wire, "guest MLS KeyPackage")?;

    let host_client = identity_client(identity)?;
    let mut host_group = host_client
        .create_group(ExtensionList::default(), Default::default(), None)
        .map_err(|error| format!("Could not create the ephemeral MLS test group: {error}"))?;
    let commit = host_group
        .commit_builder()
        .add_member(guest_key_package)
        .map_err(|error| format!("Could not add the guest KeyPackage: {error}"))?
        .build()
        .map_err(|error| format!("Could not build the MLS welcome: {error}"))?;
    host_group
        .apply_pending_commit()
        .map_err(|error| format!("Could not apply the ephemeral MLS commit: {error}"))?;
    let welcome = commit
        .welcome_messages
        .first()
        .ok_or_else(|| "The MLS commit did not produce a test Welcome.".to_owned())?
        .to_bytes()
        .map_err(|error| format!("Could not serialize the MLS Welcome: {error}"))?;
    send_frame(&mut stream, &welcome)?;

    let (prompt, pin) = safety_prompt(&host_group, pins)?;
    if let Some(stopped) = verify_safety_code(&mut stream, &mut host_group, &prompt, decide)? {
        return Ok(SessionOutcome::stopped(stopped));
    }

    let guest_wire = receive_frame(&mut stream)?;
    let guest_message = parse_mls_message(&guest_wire, "guest test message")?;
    let received = host_group
        .process_incoming_message(guest_message)
        .map_err(|error| format!("Host rejected the guest MLS test message: {error}"))?;
    verify_application_message(received, GUEST_TO_HOST, "Guest-to-host message")?;

    let host_wire = encrypt_test_message(&mut host_group, HOST_TO_GUEST, "host-to-guest message")?;
    send_frame(&mut stream, &host_wire)?;

    let guest_ack_wire = receive_frame(&mut stream)?;
    let guest_ack = parse_mls_message(&guest_ack_wire, "guest acknowledgement")?;
    let received = host_group
        .process_incoming_message(guest_ack)
        .map_err(|error| format!("Host rejected the guest MLS acknowledgement: {error}"))?;
    verify_application_message(received, GUEST_ACK, "Guest acknowledgement")?;
    send_frame(&mut stream, b"PASS")?;

    run_typed_test(
        profile,
        &mut stream,
        &mut host_group,
        &mut chat,
        pin.clone(),
    );
    Ok(SessionOutcome {
        status: LAN_PASS_STATUS.into(),
        verified_peer: Some(pin),
    })
}

fn guest_session<D, S, T>(
    profile: &LanProfile,
    mut stream: T,
    token: &str,
    identity: LanIdentity,
    pins: &str,
    decide: D,
    mut chat: S,
) -> Result<SessionOutcome, String>
where
    D: FnOnce(&SafetyPrompt) -> Result<LocalAnswer, String>,
    S: ChatSide,
    T: PeerStream,
{
    configure_stream(&stream)?;
    send_frame(&mut stream, guest_hello(profile, token).as_bytes())?;

    let guest_client = identity_client(identity)?;
    let key_package = guest_client
        .generate_key_package_message(Default::default(), Default::default(), None)
        .map_err(|error| format!("Could not create a guest MLS KeyPackage: {error}"))?;
    let key_package_wire = key_package
        .to_bytes()
        .map_err(|error| format!("Could not serialize the guest MLS KeyPackage: {error}"))?;
    send_frame(&mut stream, &key_package_wire)?;

    let welcome_wire = receive_frame(&mut stream)?;
    let welcome = parse_mls_message(&welcome_wire, "host MLS Welcome")?;
    let (mut guest_group, _) = guest_client
        .join_group(None, &welcome, None)
        .map_err(|error| format!("Could not join the ephemeral MLS test group: {error}"))?;

    let (prompt, pin) = safety_prompt(&guest_group, pins)?;
    if let Some(stopped) = verify_safety_code(&mut stream, &mut guest_group, &prompt, decide)? {
        return Ok(SessionOutcome::stopped(stopped));
    }

    let guest_wire =
        encrypt_test_message(&mut guest_group, GUEST_TO_HOST, "guest-to-host message")?;
    send_frame(&mut stream, &guest_wire)?;

    let host_wire = receive_frame(&mut stream)?;
    let host_message = parse_mls_message(&host_wire, "host test message")?;
    let received = guest_group
        .process_incoming_message(host_message)
        .map_err(|error| format!("Guest rejected the host MLS test message: {error}"))?;
    verify_application_message(received, HOST_TO_GUEST, "Host-to-guest message")?;

    let ack_wire = encrypt_test_message(&mut guest_group, GUEST_ACK, "guest acknowledgement")?;
    send_frame(&mut stream, &ack_wire)?;
    if receive_frame(&mut stream)?.as_slice() != b"PASS" {
        return Err("Host did not confirm the complete MLS test exchange.".into());
    }

    run_typed_test(
        profile,
        &mut stream,
        &mut guest_group,
        &mut chat,
        pin.clone(),
    );
    Ok(SessionOutcome {
        status: LAN_PASS_STATUS.into(),
        verified_peer: Some(pin),
    })
}

/// Keep a verified peer for the test APK to remember, and return the status for its screen.
fn finish_session(outcome: SessionOutcome) -> String {
    set_verified_peer(outcome.verified_peer);
    outcome.status
}

/// Start a one-peer MLS test host on a private IPv4 address with this install's identity.
/// The listener closes when one test completes, after three minutes, or when the screen stops
/// the wait ([`lan_cancel_host_wait`]). After a PASS the typed test runs until a person ends it;
/// the returned status covers the verified exchange.
pub fn run_lan_mls_host(
    profile: &LanProfile,
    host_ip: &str,
    port: u16,
    token: &str,
    identity: &str,
    pins: &str,
) -> Result<String, String> {
    set_verified_peer(None);
    reset_screen_chat(profile);
    validate_invite_token(token)?;
    let identity = parse_lan_identity(identity)?;
    let host_ip = require_private_ipv4(host_ip)?;
    if port == 0 {
        return Err("A valid non-zero local test port is required.".into());
    }
    let listener = TcpListener::bind(SocketAddrV4::new(host_ip, port))
        .map_err(|error| format!("Could not start the local Wi-Fi test host: {error}"))?;
    listener
        .set_nonblocking(true)
        .map_err(|error| format!("Could not configure the local Wi-Fi listener: {error}"))?;
    HOST_CANCELLED.store(false, Ordering::SeqCst);
    HOST_WAITING.store(true, Ordering::SeqCst);
    let accepted = accept_with_timeout(&listener, HOST_WAIT, &HOST_CANCELLED);
    HOST_WAITING.store(false, Ordering::SeqCst);
    let stream = accepted?;
    host_session(
        profile,
        stream,
        token,
        identity,
        pins,
        ask_local_user,
        ScreenChat,
    )
    .map(finish_session)
}

/// Join a one-peer test host on a private LAN IPv4 address with this install's identity.
pub fn run_lan_mls_join(
    profile: &LanProfile,
    host_ip: &str,
    port: u16,
    token: &str,
    identity: &str,
    pins: &str,
) -> Result<String, String> {
    set_verified_peer(None);
    reset_screen_chat(profile);
    validate_invite_token(token)?;
    let identity = parse_lan_identity(identity)?;
    let host_ip = require_private_ipv4(host_ip)?;
    if port == 0 {
        return Err("A valid non-zero local test port is required.".into());
    }
    let address = SocketAddr::V4(SocketAddrV4::new(host_ip, port));
    let stream = TcpStream::connect_timeout(&address, CONNECT_TIMEOUT)
        .map_err(|error| format!("Could not connect to the local Wi-Fi test host: {error}"))?;
    guest_session(
        profile,
        stream,
        token,
        identity,
        pins,
        ask_local_user,
        ScreenChat,
    )
    .map(finish_session)
}

/// The session's end of the local socket pair prepared for the next chat through the Internet
/// relay (see [`open_relay_pipe`]).
#[cfg(unix)]
static RELAY_PIPE: Mutex<Option<UnixStream>> = Mutex::new(None);

/// Prepare one chat through the Internet relay: a local socket pair. The session keeps one end
/// for the next [`run_relay_mls_host`] or [`run_relay_mls_join`]; the caller gets the other end
/// and copies bytes between it and the relay's WebSocket, in both directions, unchanged. A pair
/// that was prepared but never used is closed and replaced.
#[cfg(unix)]
pub fn open_relay_pipe() -> Result<UnixStream, String> {
    let (session_end, app_end) = UnixStream::pair()
        .map_err(|error| format!("Could not prepare the connection to the relay: {error}"))?;
    let mut prepared = RELAY_PIPE
        .lock()
        .map_err(|_| "Could not prepare the connection to the relay.".to_owned())?;
    *prepared = Some(session_end);
    Ok(app_end)
}

#[cfg(unix)]
fn take_relay_pipe() -> Result<UnixStream, String> {
    RELAY_PIPE
        .lock()
        .ok()
        .and_then(|mut prepared| prepared.take())
        .ok_or_else(|| "No relay connection was prepared for this chat.".to_owned())
}

/// Host one chat through the Internet relay, over the pipe from [`open_relay_pipe`]. The app
/// calls this once the relay reports that the other phone has joined; from then on the session
/// is the same as [`run_lan_mls_host`] after a phone connected, so the relay only ever carries
/// the same frames as the Wi-Fi connection.
#[cfg(unix)]
pub fn run_relay_mls_host(
    profile: &LanProfile,
    token: &str,
    identity: &str,
    pins: &str,
) -> Result<String, String> {
    let stream = take_relay_pipe()?;
    set_verified_peer(None);
    reset_screen_chat(profile);
    validate_invite_token(token)?;
    let identity = parse_lan_identity(identity)?;
    host_session(
        profile,
        stream,
        token,
        identity,
        pins,
        ask_local_user,
        ScreenChat,
    )
    .map(finish_session)
}

/// Join one chat through the Internet relay, over the pipe from [`open_relay_pipe`], once the
/// relay reports that both phones are in the room; otherwise the same as [`run_lan_mls_join`].
#[cfg(unix)]
pub fn run_relay_mls_join(
    profile: &LanProfile,
    token: &str,
    identity: &str,
    pins: &str,
) -> Result<String, String> {
    let stream = take_relay_pipe()?;
    set_verified_peer(None);
    reset_screen_chat(profile);
    validate_invite_token(token)?;
    let identity = parse_lan_identity(identity)?;
    guest_session(
        profile,
        stream,
        token,
        identity,
        pins,
        ask_local_user,
        ScreenChat,
    )
    .map(finish_session)
}

#[cfg(test)]
mod tests {
    use super::{
        accept_with_timeout, ask_local_user, check_guest_hello, classify_peer, connection_failure,
        encrypt_chat, from_hex, generate_lan_identity, group_safety_code, guest_hello,
        guest_session, host_session, identity_client, lan_chat_end, lan_chat_poll, lan_chat_send,
        lan_safety_prompt, open_relay_pipe, parse_lan_identity, peer_identity, receive_frame,
        reset_screen_chat, run_relay_mls_host, run_relay_mls_join, run_typed_test,
        safety_code_from_authenticator, submit_lan_safety_decision, take_lan_verified_peer,
        take_relay_pipe, to_hex, Answer, ChatCommand, ChatEvent, ChatPayload, ChatSide, LanProfile,
        LocalAnswer, PartialFrame, PeerStatus, SafetyPrompt, ScreenChat, SessionOutcome, CHAT_ACK,
        CHAT_END, CHAT_TEXT, KEEP_APP_OPEN_HINT, LAN_PASS_STATUS, LOCAL_REJECTED_STATUS,
        LOCAL_UNANSWERED_STATUS, MAX_TYPED_MESSAGES, MAX_TYPED_MESSAGE_BYTES, PEER_REJECTED_STATUS,
        PEER_UNANSWERED_STATUS, SMOKE_TEST, VERIFY_TIMEOUT, WIFI_CHAT,
    };
    use mls_rs::client_builder::MlsConfig;
    use mls_rs::group::{Group, ReceivedMessage};
    use mls_rs::{Client, ExtensionList, MlsMessage};
    use std::io::{Read, Write};
    use std::net::{Ipv4Addr, Shutdown, TcpListener, TcpStream};
    use std::os::unix::net::UnixStream;
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::{mpsc, Arc};
    use std::thread;
    use std::time::{Duration, Instant};

    const LOOPBACK_TOKEN: &str = "0123456789abcdef0123456789abcdef";
    const HOST_DEVICE: &str = "00112233445566aa";
    const GUEST_DEVICE: &str = "8899aabbccddeeff";

    /// One phone in a loopback run: its identity text, remembered peers and scripted person.
    struct Side {
        identity: String,
        pins: String,
        /// The person's answer, or `None` if they never answer.
        answer: Option<bool>,
        /// How long the person takes to answer.
        delay: Duration,
        /// How long the person has to answer.
        time_limit: Duration,
    }

    impl Side {
        fn new(identity: &str, pins: &str, answer: bool) -> Self {
            Self {
                identity: identity.to_owned(),
                pins: pins.to_owned(),
                answer: Some(answer),
                delay: Duration::ZERO,
                time_limit: VERIFY_TIMEOUT,
            }
        }

        /// This person never answers; their comparison times out after `time_limit`.
        fn silent(identity: &str, pins: &str, time_limit: Duration) -> Self {
            Self {
                answer: None,
                time_limit,
                ..Self::new(identity, pins, false)
            }
        }

        /// This person answers only after `delay`.
        fn after(self, delay: Duration) -> Self {
            Self { delay, ..self }
        }

        /// The scripted person's answers, and the sender that must outlive the session so a
        /// silent person stays silent instead of disconnecting.
        fn person(&self) -> (LocalAnswer, mpsc::Sender<bool>) {
            let (answer, answers) = mpsc::channel();
            match self.answer {
                // An immediate answer is queued before the comparison starts, so it comes first.
                Some(codes_match) if self.delay.is_zero() => {
                    answer.send(codes_match).expect("queue the scripted answer")
                }
                Some(codes_match) => {
                    let later = answer.clone();
                    let delay = self.delay;
                    thread::spawn(move || {
                        thread::sleep(delay);
                        let _ = later.send(codes_match);
                    });
                }
                None => {}
            }
            let local = LocalAnswer {
                answers,
                time_limit: self.time_limit,
                on_screen: false,
            };
            (local, answer)
        }
    }

    /// One phone's person in a loopback typed test, driven by the test through channels.
    struct ScriptedChat {
        commands: mpsc::Receiver<ChatCommand>,
        events: mpsc::Sender<ChatEvent>,
    }

    impl ChatSide for ScriptedChat {
        fn next_command(&mut self) -> Option<ChatCommand> {
            self.commands.try_recv().ok()
        }

        fn report(&mut self, event: ChatEvent) {
            // The test may already have stopped listening; the session must not care.
            let _ = self.events.send(event);
        }
    }

    /// A scripted person, plus the test's handles to give them commands and watch their screen.
    fn scripted_chat() -> (
        ScriptedChat,
        mpsc::Sender<ChatCommand>,
        mpsc::Receiver<ChatEvent>,
    ) {
        let (command_sender, commands) = mpsc::channel();
        let (events, event_receiver) = mpsc::channel();
        (
            ScriptedChat { commands, events },
            command_sender,
            event_receiver,
        )
    }

    fn next_event(events: &mpsc::Receiver<ChatEvent>) -> ChatEvent {
        events
            .recv_timeout(Duration::from_secs(20))
            .expect("the typed test reported an event in time")
    }

    /// What one phone showed and reported in a loopback run.
    struct Seen {
        outcome: SessionOutcome,
        code: String,
        peer_status: PeerStatus,
        peer_device: String,
        typing_opened: bool,
        typed_test: Option<String>,
    }

    impl Seen {
        fn new(outcome: SessionOutcome, prompt: Option<(String, PeerStatus, String)>) -> Self {
            let (code, peer_status, peer_device) = prompt.expect("the phone showed a safety code");
            Self {
                outcome,
                code,
                peer_status,
                peer_device,
                typing_opened: false,
                typed_test: None,
            }
        }

        /// Add what this phone's typed-test screen was told during the run.
        fn with_screen(mut self, events: &mpsc::Receiver<ChatEvent>) -> Self {
            for event in events.try_iter() {
                match event {
                    ChatEvent::Open { .. } => self.typing_opened = true,
                    ChatEvent::Ended(summary) => self.typed_test = Some(summary),
                    ChatEvent::Delivered(_) | ChatEvent::Received(..) => {}
                }
            }
            self
        }
    }

    fn shown(prompt: &SafetyPrompt) -> Option<(String, PeerStatus, String)> {
        Some((
            prompt.code.clone(),
            prompt.peer_status,
            prompt.peer_device.clone(),
        ))
    }

    /// Start both phones on loopback, each in its own thread, with the given typed-test people.
    fn start_loopback(
        host: Side,
        host_chat: ScriptedChat,
        guest: Side,
        guest_chat: ScriptedChat,
    ) -> (thread::JoinHandle<Seen>, thread::JoinHandle<Seen>) {
        start_loopback_with(&SMOKE_TEST, host, host_chat, guest, guest_chat)
    }

    /// Run both phones of one app (smoke test or Wi-Fi chat) over loopback.
    fn start_loopback_with(
        profile: &'static LanProfile,
        host: Side,
        host_chat: ScriptedChat,
        guest: Side,
        guest_chat: ScriptedChat,
    ) -> (thread::JoinHandle<Seen>, thread::JoinHandle<Seen>) {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0))
            .expect("bind a host-only loopback MLS test listener");
        listener
            .set_nonblocking(true)
            .expect("use non-blocking accept with a bounded wait");
        let address = listener.local_addr().expect("read the loopback address");
        let host_identity = parse_lan_identity(&host.identity).expect("parse the host identity");
        let host_thread = thread::spawn(move || {
            let deadline = Instant::now() + Duration::from_secs(20);
            let stream = loop {
                match listener.accept() {
                    Ok((stream, _)) => break stream,
                    Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                        assert!(Instant::now() < deadline, "test guest did not connect");
                        thread::sleep(Duration::from_millis(10));
                    }
                    Err(error) => panic!("loopback accept failed: {error}"),
                }
            };
            let (local, _person) = host.person();
            let mut prompt = None;
            let outcome = host_session(
                profile,
                stream,
                LOOPBACK_TOKEN,
                host_identity,
                &host.pins,
                |seen: &SafetyPrompt| {
                    prompt = shown(seen);
                    Ok(local)
                },
                host_chat,
            )
            .expect("host finished the MLS LAN exchange");
            Seen::new(outcome, prompt)
        });

        let guest_identity = parse_lan_identity(&guest.identity).expect("parse the guest identity");
        let guest_thread = thread::spawn(move || {
            let stream = TcpStream::connect_timeout(&address, Duration::from_secs(5))
                .expect("connect the independent guest to the local test host");
            let (local, _person) = guest.person();
            let mut prompt = None;
            let outcome = guest_session(
                profile,
                stream,
                LOOPBACK_TOKEN,
                guest_identity,
                &guest.pins,
                |seen: &SafetyPrompt| {
                    prompt = shown(seen);
                    Ok(local)
                },
                guest_chat,
            )
            .expect("guest finished the MLS LAN exchange");
            Seen::new(outcome, prompt)
        });
        (host_thread, guest_thread)
    }

    /// Run both phones; if typing opens, the host's person ends it straight away.
    fn run_loopback(host: Side, guest: Side) -> (Seen, Seen) {
        let (host_chat, host_commands, host_screen) = scripted_chat();
        let (guest_chat, _guest_commands, guest_screen) = scripted_chat();
        host_commands
            .send(ChatCommand::End)
            .expect("queue the host's end of the typed test");
        let (host_thread, guest_thread) = start_loopback(host, host_chat, guest, guest_chat);
        let host_seen = host_thread.join().expect("host thread did not panic");
        let guest_seen = guest_thread.join().expect("guest thread did not panic");
        (
            host_seen.with_screen(&host_screen),
            guest_seen.with_screen(&guest_screen),
        )
    }

    fn assert_safety_code_format(code: &str) {
        let groups: Vec<&str> = code.split(' ').collect();
        assert_eq!(groups.len(), 4, "{code}");
        assert!(
            groups
                .iter()
                .all(|group| group.len() == 5 && group.bytes().all(|byte| byte.is_ascii_digit())),
            "{code}"
        );
    }

    /// Every client in a test is built here, so they share one concrete configuration type.
    fn client_from(identity_text: &str) -> Client<impl MlsConfig> {
        identity_client(parse_lan_identity(identity_text).expect("parse the test identity"))
            .expect("build a test-only MLS client")
    }

    fn new_identity(device_id: &str) -> String {
        generate_lan_identity(device_id).expect("generate a test identity")
    }

    /// The creator makes a group and adds the joiner, crossing a serialized boundary as on the LAN.
    fn form_group<C: MlsConfig>(creator: &Client<C>, joiner: &Client<C>) -> (Group<C>, Group<C>) {
        let key_package = joiner
            .generate_key_package_message(Default::default(), Default::default(), None)
            .expect("generate the joiner's KeyPackage");
        let key_package = MlsMessage::from_bytes(
            &key_package
                .to_bytes()
                .expect("serialize the joiner's KeyPackage"),
        )
        .expect("deserialize the joiner's KeyPackage");
        let mut creator_group = creator
            .create_group(ExtensionList::default(), Default::default(), None)
            .expect("create the test group");
        let commit = creator_group
            .commit_builder()
            .add_member(key_package)
            .expect("stage the joiner's KeyPackage")
            .build()
            .expect("build the add-member commit");
        creator_group
            .apply_pending_commit()
            .expect("apply the add-member commit");
        let welcome = MlsMessage::from_bytes(
            &commit.welcome_messages[0]
                .to_bytes()
                .expect("serialize the Welcome"),
        )
        .expect("deserialize the Welcome");
        let (joiner_group, _) = joiner
            .join_group(None, &welcome, None)
            .expect("join the test group");
        (creator_group, joiner_group)
    }

    #[test]
    fn safety_codes_are_four_groups_of_five_digits() {
        let authenticator: Vec<u8> = (0u8..32).collect();
        let code = safety_code_from_authenticator(&authenticator).expect("format a safety code");
        assert_eq!(code, "09060 60585 12110 63635");
        assert_safety_code_format(&code);
        assert!(safety_code_from_authenticator(&authenticator[..19]).is_err());
    }

    #[test]
    fn identity_text_round_trips_and_rejects_malformed_values() {
        let text = generate_lan_identity(HOST_DEVICE).expect("generate a test identity");
        assert!(text.starts_with("v1:00112233445566aa:"), "{text}");
        let identity = parse_lan_identity(&text).expect("parse the generated identity");
        assert_eq!(identity.device_id, HOST_DEVICE);
        for malformed in [
            "",
            "v1",
            "v2:00112233445566aa:00:00",
            "v1:0011:00:00",
            "v1:00112233445566AA:00:00",
            "v1:00112233445566aa:zz:00",
            "v1:00112233445566aa:00:00:00",
            "v1:00112233445566aa::00",
        ] {
            assert!(parse_lan_identity(malformed).is_err(), "{malformed}");
        }
        assert!(generate_lan_identity("not-a-device-id!").is_err());
        assert_eq!(to_hex(&[0x00, 0x0f, 0xa5, 0xff]), "000fa5ff");
        assert_eq!(
            from_hex("000fA5ff", "test value"),
            Ok(vec![0x00, 0x0f, 0xa5, 0xff])
        );
        assert!(from_hex("abc", "test value").is_err());
    }

    #[test]
    fn peers_are_new_known_or_changed() {
        let key = [0x01, 0x02, 0x03];
        let pins = format!("{GUEST_DEVICE} {}\n1111111111111111 0a0b\n", to_hex(&key));
        assert_eq!(classify_peer(GUEST_DEVICE, &key, &pins), PeerStatus::Known);
        assert_eq!(
            classify_peer(GUEST_DEVICE, &[0x09], &pins),
            PeerStatus::KeyChanged
        );
        assert_eq!(classify_peer(HOST_DEVICE, &key, &pins), PeerStatus::New);
        assert_eq!(classify_peer(GUEST_DEVICE, &key, ""), PeerStatus::New);
        assert_eq!(
            classify_peer(GUEST_DEVICE, &key, "  \nnot a pin line\n"),
            PeerStatus::New
        );
    }

    #[test]
    fn remembered_phones_are_recognized_and_a_changed_key_is_flagged() {
        let host_identity = generate_lan_identity(HOST_DEVICE).expect("host identity");
        let guest_identity = generate_lan_identity(GUEST_DEVICE).expect("guest identity");

        // First meeting: the phones are new to each other and remember each other after PASS.
        let (host, guest) = run_loopback(
            Side::new(&host_identity, "", true),
            Side::new(&guest_identity, "", true),
        );
        assert_eq!(host.outcome.status, LAN_PASS_STATUS);
        assert_eq!(guest.outcome.status, LAN_PASS_STATUS);
        assert_eq!(host.code, guest.code, "both phones show the same code");
        assert_safety_code_format(&host.code);
        assert_eq!(host.peer_status, PeerStatus::New);
        assert_eq!(guest.peer_status, PeerStatus::New);
        assert_eq!(host.peer_device, GUEST_DEVICE);
        assert_eq!(guest.peer_device, HOST_DEVICE);
        let host_pins = host
            .outcome
            .verified_peer
            .expect("the host remembers the guest");
        let guest_pins = guest
            .outcome
            .verified_peer
            .expect("the guest remembers the host");
        assert!(host_pins.starts_with(GUEST_DEVICE), "{host_pins}");
        assert!(guest_pins.starts_with(HOST_DEVICE), "{guest_pins}");
        assert!(
            host.typing_opened && guest.typing_opened,
            "typing opens after a PASS"
        );
        let host_typed = host.typed_test.unwrap_or_default();
        let guest_typed = guest.typed_test.unwrap_or_default();
        assert!(
            host_typed.starts_with("Ended on this phone. Sent 0 (0 delivered), received 0, in "),
            "{host_typed}"
        );
        assert!(
            guest_typed
                .starts_with("Ended on the other phone. Sent 0 (0 delivered), received 0, in "),
            "{guest_typed}"
        );

        // Second meeting: each phone recognizes the key it verified before.
        let (host, guest) = run_loopback(
            Side::new(&host_identity, &host_pins, true),
            Side::new(&guest_identity, &guest_pins, true),
        );
        assert_eq!(host.peer_status, PeerStatus::Known);
        assert_eq!(guest.peer_status, PeerStatus::Known);
        assert_eq!(guest.outcome.status, LAN_PASS_STATUS);

        // The host resets its key but keeps its device ID: the guest must see a changed key.
        let reset_host_identity = generate_lan_identity(HOST_DEVICE).expect("new host key");
        let (host, guest) = run_loopback(
            Side::new(&reset_host_identity, &host_pins, true),
            Side::new(&guest_identity, &guest_pins, false),
        );
        assert_eq!(host.peer_status, PeerStatus::Known);
        assert_eq!(guest.peer_status, PeerStatus::KeyChanged);
        assert_eq!(guest.outcome.status, LOCAL_REJECTED_STATUS);
        assert_eq!(host.outcome.status, PEER_REJECTED_STATUS);
        assert!(guest.outcome.verified_peer.is_none());
        assert!(host.outcome.verified_peer.is_none());
        assert!(!host.typing_opened && !guest.typing_opened);
        assert!(host.typed_test.is_none() && guest.typed_test.is_none());
    }

    #[test]
    fn a_reported_mismatch_stops_both_phones_before_any_test_message() {
        let host_identity = generate_lan_identity(HOST_DEVICE).expect("host identity");
        let guest_identity = generate_lan_identity(GUEST_DEVICE).expect("guest identity");

        let (host, guest) = run_loopback(
            Side::new(&host_identity, "", false),
            Side::new(&guest_identity, "", true),
        );
        assert_eq!(host.outcome.status, LOCAL_REJECTED_STATUS);
        assert_eq!(guest.outcome.status, PEER_REJECTED_STATUS);
        assert_eq!(host.code, guest.code);
        assert!(
            !host.typing_opened && !guest.typing_opened,
            "no typing after a reported mismatch"
        );

        let (host, guest) = run_loopback(
            Side::new(&host_identity, "", false),
            Side::new(&guest_identity, "", false),
        );
        assert_eq!(host.outcome.status, LOCAL_REJECTED_STATUS);
        assert_eq!(guest.outcome.status, LOCAL_REJECTED_STATUS);
        assert!(host.outcome.verified_peer.is_none());
        assert!(guest.outcome.verified_peer.is_none());
    }

    #[test]
    fn a_relaying_device_shows_a_different_code_and_a_changed_key() {
        // A device in the middle must join the real host's group posing as its guest and invite
        // the real guest into a second group of its own.
        let guest_text = new_identity(GUEST_DEVICE);
        let real_guest_key = parse_lan_identity(&guest_text)
            .expect("parse the real guest identity")
            .public_key
            .as_bytes()
            .to_vec();
        let host = client_from(&new_identity(HOST_DEVICE));
        let guest = client_from(&guest_text);
        let relay_posing_as_guest = client_from(&new_identity(GUEST_DEVICE));
        let relay_posing_as_host = client_from(&new_identity(HOST_DEVICE));

        let (host_group, relay_first_group) = form_group(&host, &relay_posing_as_guest);
        let (relay_second_group, guest_group) = form_group(&relay_posing_as_host, &guest);

        let host_code = group_safety_code(&host_group).expect("the host's safety code");
        let guest_code = group_safety_code(&guest_group).expect("the guest's safety code");
        assert_eq!(
            host_code,
            group_safety_code(&relay_first_group).expect("the relay's code in the host's group")
        );
        assert_eq!(
            guest_code,
            group_safety_code(&relay_second_group).expect("the relay's code in the guest's group")
        );
        assert_ne!(
            host_code, guest_code,
            "a relaying device must not be able to make both phones show one code"
        );

        // A host that verified the real guest before also sees the relay's key as changed.
        let (relay_device, relay_key) = peer_identity(&host_group).expect("the host's peer");
        assert_eq!(relay_device, GUEST_DEVICE);
        let host_pins = format!("{GUEST_DEVICE} {}", to_hex(&real_guest_key));
        assert_eq!(
            classify_peer(&relay_device, &relay_key, &host_pins),
            PeerStatus::KeyChanged
        );
    }

    #[test]
    fn the_screen_receives_the_prompt_and_returns_the_answer() {
        assert_eq!(lan_safety_prompt(), None);
        assert!(!submit_lan_safety_decision(true), "nothing is waiting yet");

        let prompt = SafetyPrompt {
            code: "12345 67890 13579 24680".into(),
            peer_device: GUEST_DEVICE.into(),
            peer_status: PeerStatus::KeyChanged,
        };
        let local = ask_local_user(&prompt).expect("offer the comparison to the screen");
        assert_eq!(local.time_limit, VERIFY_TIMEOUT);
        assert_eq!(
            lan_safety_prompt().as_deref(),
            Some("12345 67890 13579 24680|CHANGED|8899aabbccddeeff")
        );
        assert_eq!(local.given(), None, "the person has not answered yet");
        assert!(submit_lan_safety_decision(true));
        assert_eq!(local.given(), Some(Answer::Confirmed));
        assert_eq!(lan_safety_prompt(), None, "an answered prompt is cleared");
        assert!(
            !submit_lan_safety_decision(false),
            "an answer is accepted only once"
        );
        drop(local);

        // When the other phone stops the test first, the question leaves the screen.
        let local = ask_local_user(&prompt).expect("offer the comparison again");
        assert!(lan_safety_prompt().is_some());
        drop(local);
        assert_eq!(lan_safety_prompt(), None, "a withdrawn prompt is cleared");
        assert!(
            !submit_lan_safety_decision(false),
            "a withdrawn prompt refuses a late answer"
        );
    }

    #[test]
    fn a_mismatch_report_stops_the_other_phone_before_its_person_answers() {
        let host_identity = new_identity(HOST_DEVICE);
        let guest_identity = new_identity(GUEST_DEVICE);

        // The other person never answers, yet their phone stops as soon as the report arrives
        // (well before its two minutes run out), and the reporting phone does not wait for them.
        for host_reports in [true, false] {
            let started = Instant::now();
            let (host, guest) = if host_reports {
                run_loopback(
                    Side::new(&host_identity, "", false),
                    Side::silent(&guest_identity, "", VERIFY_TIMEOUT),
                )
            } else {
                run_loopback(
                    Side::silent(&host_identity, "", VERIFY_TIMEOUT),
                    Side::new(&guest_identity, "", false),
                )
            };
            let (reporter, other) = if host_reports {
                (host, guest)
            } else {
                (guest, host)
            };
            assert!(
                started.elapsed() < Duration::from_secs(10),
                "both phones stopped at once, not after the time limit"
            );
            assert_eq!(reporter.outcome.status, LOCAL_REJECTED_STATUS);
            assert_eq!(other.outcome.status, PEER_REJECTED_STATUS);
            assert_eq!(reporter.code, other.code);
            assert!(reporter.outcome.verified_peer.is_none());
            assert!(other.outcome.verified_peer.is_none());
            assert!(!reporter.typing_opened && !other.typing_opened);
        }

        // A person who reports a mismatch after the other phone confirmed also stops both.
        let (host, guest) = run_loopback(
            Side::new(&host_identity, "", true),
            Side::new(&guest_identity, "", false).after(Duration::from_millis(400)),
        );
        assert_eq!(guest.outcome.status, LOCAL_REJECTED_STATUS);
        assert_eq!(host.outcome.status, PEER_REJECTED_STATUS);
        assert!(!host.typing_opened && !guest.typing_opened);
    }

    #[test]
    fn an_unanswered_comparison_stops_both_phones_and_says_which_phone_did_not_answer() {
        let host_identity = new_identity(HOST_DEVICE);
        let guest_identity = new_identity(GUEST_DEVICE);
        let short_limit = Duration::from_millis(400);

        let (host, guest) = run_loopback(
            Side::new(&host_identity, "", true),
            Side::silent(&guest_identity, "", short_limit),
        );
        assert_eq!(guest.outcome.status, LOCAL_UNANSWERED_STATUS);
        assert_eq!(host.outcome.status, PEER_UNANSWERED_STATUS);
        assert!(host.outcome.verified_peer.is_none() && guest.outcome.verified_peer.is_none());

        let (host, guest) = run_loopback(
            Side::silent(&host_identity, "", short_limit),
            Side::new(&guest_identity, "", true),
        );
        assert_eq!(host.outcome.status, LOCAL_UNANSWERED_STATUS);
        assert_eq!(guest.outcome.status, PEER_UNANSWERED_STATUS);
        assert!(!host.typing_opened && !guest.typing_opened);
    }

    #[test]
    fn a_late_confirmation_still_passes_after_the_other_phone_confirmed() {
        let host_identity = new_identity(HOST_DEVICE);
        let guest_identity = new_identity(GUEST_DEVICE);
        let pause = Duration::from_millis(400);
        for (host_delay, guest_delay) in [(pause, Duration::ZERO), (Duration::ZERO, pause)] {
            let (host, guest) = run_loopback(
                Side::new(&host_identity, "", true).after(host_delay),
                Side::new(&guest_identity, "", true).after(guest_delay),
            );
            assert_eq!(host.outcome.status, LAN_PASS_STATUS);
            assert_eq!(guest.outcome.status, LAN_PASS_STATUS);
            assert!(host.typing_opened && guest.typing_opened);
        }
    }

    #[test]
    fn a_safety_code_answer_is_read_in_pieces_without_taking_the_next_frame() {
        let listener =
            TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).expect("bind a loopback listener");
        let mut writer = TcpStream::connect(listener.local_addr().expect("read the address"))
            .expect("connect the other phone");
        let (mut reader, _) = listener.accept().expect("accept the other phone");
        reader
            .set_read_timeout(Some(Duration::from_millis(50)))
            .expect("use a short read timeout");
        writer.set_nodelay(true).expect("send small pieces at once");

        let mut answer = PartialFrame::default();
        assert_eq!(answer.poll(&mut reader), Ok(None), "nothing has arrived");
        for piece in [&[0u8, 0][..], &[0, 3, b'a'][..]] {
            writer.write_all(piece).expect("send part of the answer");
            thread::sleep(Duration::from_millis(20));
            assert_eq!(
                answer.poll(&mut reader),
                Ok(None),
                "the answer is incomplete"
            );
        }
        // The rest of the answer arrives together with the start of the next frame.
        writer
            .write_all(&[b'b', b'c', 0, 0, 0, 1, b'z'])
            .expect("send the rest and the next frame");
        thread::sleep(Duration::from_millis(20));
        assert_eq!(answer.poll(&mut reader), Ok(Some(b"abc".to_vec())));
        reader
            .set_read_timeout(Some(Duration::from_secs(5)))
            .expect("restore a normal read timeout");
        assert_eq!(receive_frame(&mut reader), Ok(b"z".to_vec()));

        drop(writer);
        reader
            .set_read_timeout(Some(Duration::from_millis(50)))
            .expect("use a short read timeout");
        let mut next = PartialFrame::default();
        let closed = next
            .poll(&mut reader)
            .expect_err("a closed connection is reported");
        assert!(closed.contains(KEEP_APP_OPEN_HINT), "{closed}");
    }

    #[test]
    fn rejects_non_private_hosts_and_malformed_invites() {
        assert!(super::require_private_ipv4("203.0.113.5").is_err());
        assert!(super::require_private_ipv4("192.168.1.5").is_ok());
        assert!(super::validate_invite_token("short").is_err());
    }

    #[test]
    fn typed_messages_flow_both_ways_after_both_people_confirm() {
        let host_identity = new_identity(HOST_DEVICE);
        let guest_identity = new_identity(GUEST_DEVICE);
        let (host_chat, host_commands, host_screen) = scripted_chat();
        let (guest_chat, guest_commands, guest_screen) = scripted_chat();
        let (host_thread, guest_thread) = start_loopback(
            Side::new(&host_identity, "", true),
            host_chat,
            Side::new(&guest_identity, "", true),
            guest_chat,
        );

        let ChatEvent::Open { verified_peer } = next_event(&host_screen) else {
            panic!("typing must open on the host before anything else");
        };
        assert!(verified_peer.starts_with(GUEST_DEVICE), "{verified_peer}");
        assert!(matches!(next_event(&guest_screen), ChatEvent::Open { .. }));

        // Tamil, an emoji, the event separator and a line break all arrive unchanged.
        let greeting = "வணக்கம்! Typed test 👋 | with a second\nline";
        guest_commands
            .send(ChatCommand::Send(1, greeting.into()))
            .expect("queue the guest's message");
        assert_eq!(
            next_event(&host_screen),
            ChatEvent::Received(1, greeting.into())
        );
        assert_eq!(next_event(&guest_screen), ChatEvent::Delivered(1));

        for (number, text) in [(1, "Reply from the host"), (2, "Second reply")] {
            host_commands
                .send(ChatCommand::Send(number, text.into()))
                .expect("queue a host reply");
        }
        assert_eq!(
            next_event(&guest_screen),
            ChatEvent::Received(1, "Reply from the host".into())
        );
        assert_eq!(
            next_event(&guest_screen),
            ChatEvent::Received(2, "Second reply".into())
        );
        assert_eq!(next_event(&host_screen), ChatEvent::Delivered(1));
        assert_eq!(next_event(&host_screen), ChatEvent::Delivered(2));

        guest_commands
            .send(ChatCommand::End)
            .expect("queue the guest's end of the typed test");
        let ChatEvent::Ended(guest_summary) = next_event(&guest_screen) else {
            panic!("the guest's typed test did not end");
        };
        let ChatEvent::Ended(host_summary) = next_event(&host_screen) else {
            panic!("the host's typed test did not end");
        };
        assert!(
            guest_summary.starts_with("Ended on this phone. Sent 1 (1 delivered), received 2, in "),
            "{guest_summary}"
        );
        assert!(
            host_summary
                .starts_with("Ended on the other phone. Sent 2 (2 delivered), received 1, in "),
            "{host_summary}"
        );
        assert!(!guest_summary.contains("Typed test") && !host_summary.contains("Reply"));

        let host = host_thread.join().expect("host thread did not panic");
        let guest = guest_thread.join().expect("guest thread did not panic");
        assert_eq!(host.outcome.status, LAN_PASS_STATUS);
        assert_eq!(guest.outcome.status, LAN_PASS_STATUS);
        assert_eq!(host.code, guest.code);
    }

    #[test]
    fn typed_text_is_encrypted_and_malformed_payloads_are_refused() {
        let host = client_from(&new_identity(HOST_DEVICE));
        let guest = client_from(&new_identity(GUEST_DEVICE));
        let (mut host_group, mut guest_group) = form_group(&host, &guest);

        let text = "Typed test text வணக்கம் 👋 with | and\nline";
        let wire = encrypt_chat(&mut host_group, &ChatPayload::Text(7, text.into()))
            .expect("encrypt a typed test message");
        assert!(
            !wire
                .windows(text.len())
                .any(|window| window == text.as_bytes()),
            "typed text must not appear in the MLS wire bytes"
        );
        let received = guest_group
            .process_incoming_message(
                MlsMessage::from_bytes(&wire).expect("parse the typed test message"),
            )
            .expect("decrypt the typed test message");
        let ReceivedMessage::ApplicationMessage(application) = received else {
            panic!("a typed test message is an MLS application message");
        };
        assert_eq!(
            ChatPayload::parse(application.data(), MAX_TYPED_MESSAGE_BYTES),
            Ok(ChatPayload::Text(7, text.into()))
        );

        for payload in [ChatPayload::Ack(7), ChatPayload::End] {
            assert_eq!(
                ChatPayload::parse(&payload.to_bytes(), MAX_TYPED_MESSAGE_BYTES),
                Ok(payload)
            );
        }
        let too_long = "a".repeat(MAX_TYPED_MESSAGE_BYTES + 1);
        for malformed in [
            Vec::new(),
            vec![CHAT_TEXT, 0, 0],
            vec![CHAT_TEXT, 0, 0, 0, 1],
            vec![CHAT_TEXT, 0, 0, 0, 1, b' '],
            vec![CHAT_TEXT, 0, 0, 0, 1, 0xff],
            vec![CHAT_ACK, 0, 0, 0, 1, b'x'],
            vec![CHAT_END, 0, 0, 0, 1],
            vec![b'X', 0, 0, 0, 1],
            ChatPayload::Text(1, too_long).to_bytes(),
        ] {
            assert!(
                ChatPayload::parse(&malformed, MAX_TYPED_MESSAGE_BYTES).is_err(),
                "{malformed:?}"
            );
        }
    }

    #[test]
    fn the_screen_can_queue_typed_messages_only_while_typing_is_open() {
        reset_screen_chat(&SMOKE_TEST);
        assert!(lan_chat_send("hello").is_err(), "nothing is open yet");
        assert!(!lan_chat_end());

        let mut screen = ScreenChat;
        let pin = format!("{GUEST_DEVICE} 0a0b");
        screen.report(ChatEvent::Open {
            verified_peer: pin.clone(),
        });
        assert_eq!(lan_chat_poll(), Some(format!("OPEN|{LAN_PASS_STATUS}")));
        assert_eq!(take_lan_verified_peer(), Some(pin));
        assert!(lan_chat_send(" \n ").is_err(), "blank messages are refused");
        assert!(lan_chat_send(&"a".repeat(MAX_TYPED_MESSAGE_BYTES + 1)).is_err());
        assert_eq!(lan_chat_send("வணக்கம்"), Ok(1));
        assert_eq!(lan_chat_send("second"), Ok(2));
        assert!(lan_chat_end());
        assert_eq!(
            screen.next_command(),
            Some(ChatCommand::Send(1, "வணக்கம்".into()))
        );
        assert_eq!(
            screen.next_command(),
            Some(ChatCommand::Send(2, "second".into()))
        );
        assert_eq!(screen.next_command(), Some(ChatCommand::End));
        assert_eq!(screen.next_command(), None);

        screen.report(ChatEvent::Received(4, "hi | there\nline".into()));
        screen.report(ChatEvent::Delivered(2));
        assert_eq!(
            lan_chat_poll().as_deref(),
            Some("RECEIVED|4|hi | there\nline")
        );
        assert_eq!(lan_chat_poll().as_deref(), Some("DELIVERED|2"));
        let summary = "Ended on this phone. Sent 2 (1 delivered), received 1, in 5 s.";
        screen.report(ChatEvent::Ended(summary.into()));
        assert!(lan_chat_send("too late").is_err());
        assert!(!lan_chat_end());
        assert_eq!(lan_chat_poll(), Some(format!("ENDED|{summary}")));
        assert_eq!(lan_chat_poll(), None);

        // Each phone may send a bounded number of messages in one test.
        screen.report(ChatEvent::Open {
            verified_peer: format!("{HOST_DEVICE} 0c0d"),
        });
        for expected in 1..=MAX_TYPED_MESSAGES {
            assert_eq!(lan_chat_send("again"), Ok(expected));
        }
        assert!(lan_chat_send("one too many").is_err());
        reset_screen_chat(&SMOKE_TEST);
        assert_eq!(lan_chat_poll(), None);
        assert!(lan_chat_send("after reset").is_err());
        let _ = take_lan_verified_peer();
    }

    #[test]
    fn a_dropped_connection_stops_the_typed_test_with_the_keep_open_hint() {
        let host = client_from(&new_identity(HOST_DEVICE));
        let guest = client_from(&new_identity(GUEST_DEVICE));
        let (mut host_group, _guest_group) = form_group(&host, &guest);
        let listener =
            TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).expect("bind a loopback listener");
        let peer = TcpStream::connect(listener.local_addr().expect("read the loopback address"))
            .expect("connect the other phone");
        let (mut stream, _) = listener.accept().expect("accept the other phone");
        drop(peer);

        let (mut chat, _commands, screen) = scripted_chat();
        let summary = run_typed_test(
            &SMOKE_TEST,
            &mut stream,
            &mut host_group,
            &mut chat,
            "peer pin".into(),
        );
        assert!(
            summary.starts_with(
                "Stopped. The other phone closed the connection without ending the typed test."
            ),
            "{summary}"
        );
        assert!(summary.contains(KEEP_APP_OPEN_HINT), "{summary}");
        assert!(
            summary.contains(" Sent 0 (0 delivered), received 0, in "),
            "{summary}"
        );
        assert_eq!(
            screen.try_recv(),
            Ok(ChatEvent::Open {
                verified_peer: "peer pin".into()
            })
        );
        assert_eq!(screen.try_recv(), Ok(ChatEvent::Ended(summary)));
    }

    #[test]
    fn the_host_names_an_older_or_different_test_apk_and_closed_connections_get_a_hint() {
        assert_eq!(
            check_guest_hello(
                &SMOKE_TEST,
                guest_hello(&SMOKE_TEST, LOOPBACK_TOKEN).as_bytes(),
                LOOPBACK_TOKEN
            ),
            Ok(())
        );
        let older = check_guest_hello(&SMOKE_TEST, LOOPBACK_TOKEN.as_bytes(), LOOPBACK_TOKEN)
            .expect_err("a guest from an older test APK");
        assert!(older.contains("older test APK"), "{older}");
        let newer = format!("secretmessage-lan-test/9 {LOOPBACK_TOKEN}");
        let different = check_guest_hello(&SMOKE_TEST, newer.as_bytes(), LOOPBACK_TOKEN)
            .expect_err("a guest from another test APK version");
        assert!(different.contains("different test APK"), "{different}");
        assert_eq!(
            check_guest_hello(
                &SMOKE_TEST,
                guest_hello(&SMOKE_TEST, "ffffffffffffffffffffffffffffffff").as_bytes(),
                LOOPBACK_TOKEN
            ),
            Err("The invite does not match the active host session.".into())
        );
        assert!(check_guest_hello(&SMOKE_TEST, &[0xff, 0xfe], LOOPBACK_TOKEN).is_err());

        let aborted = std::io::Error::from(std::io::ErrorKind::ConnectionAborted);
        assert!(
            connection_failure("send the LAN test frame", &aborted).contains(KEEP_APP_OPEN_HINT)
        );
        let refused = std::io::Error::from(std::io::ErrorKind::ConnectionRefused);
        assert!(
            !connection_failure("send the LAN test frame", &refused).contains(KEEP_APP_OPEN_HINT)
        );
    }

    #[test]
    fn the_wifi_chat_build_carries_large_encoded_messages_both_ways() {
        let host_identity = new_identity(HOST_DEVICE);
        let guest_identity = new_identity(GUEST_DEVICE);
        let (host_chat, host_commands, host_screen) = scripted_chat();
        let (guest_chat, guest_commands, guest_screen) = scripted_chat();
        let (host_thread, guest_thread) = start_loopback_with(
            &WIFI_CHAT,
            Side::new(&host_identity, "", true),
            host_chat,
            Side::new(&guest_identity, "", true),
            guest_chat,
        );
        assert!(matches!(next_event(&host_screen), ChatEvent::Open { .. }));
        assert!(matches!(next_event(&guest_screen), ChatEvent::Open { .. }));

        // The app sends a drawing as encoded text, far longer than a smoke-test message.
        let drawing: String = (0..90_000u32)
            .map(|index| char::from(b'A' + (index % 26) as u8))
            .collect();
        assert!(drawing.len() > MAX_TYPED_MESSAGE_BYTES * 50);
        guest_commands
            .send(ChatCommand::Send(1, drawing.clone()))
            .expect("queue the guest's drawing");
        assert_eq!(
            next_event(&host_screen),
            ChatEvent::Received(1, drawing.clone())
        );
        assert_eq!(next_event(&guest_screen), ChatEvent::Delivered(1));

        let reply: String = drawing.chars().rev().take(70_000).collect();
        host_commands
            .send(ChatCommand::Send(1, reply.clone()))
            .expect("queue the host's reply");
        assert_eq!(next_event(&guest_screen), ChatEvent::Received(1, reply));
        assert_eq!(next_event(&host_screen), ChatEvent::Delivered(1));

        host_commands
            .send(ChatCommand::End)
            .expect("queue the host's end of the chat");
        let ChatEvent::Ended(host_summary) = next_event(&host_screen) else {
            panic!("the host's chat did not end");
        };
        let ChatEvent::Ended(guest_summary) = next_event(&guest_screen) else {
            panic!("the guest's chat did not end");
        };
        assert!(
            host_summary.starts_with("Ended on this phone. Sent 1 (1 delivered), received 1, in "),
            "{host_summary}"
        );
        assert!(
            guest_summary
                .starts_with("Ended on the other phone. Sent 1 (1 delivered), received 1, in "),
            "{guest_summary}"
        );
        let host = host_thread.join().expect("host thread did not panic");
        let guest = guest_thread.join().expect("guest thread did not panic");
        assert_eq!(host.outcome.status, LAN_PASS_STATUS);
        assert_eq!(guest.outcome.status, LAN_PASS_STATUS);
        assert_eq!(host.code, guest.code);
    }

    #[test]
    fn the_smoke_test_and_the_wifi_chat_build_refuse_each_others_phones() {
        let smoke = guest_hello(&SMOKE_TEST, LOOPBACK_TOKEN);
        let wifi = guest_hello(&WIFI_CHAT, LOOPBACK_TOKEN);
        assert_eq!(
            check_guest_hello(&WIFI_CHAT, wifi.as_bytes(), LOOPBACK_TOKEN),
            Ok(())
        );
        assert!(check_guest_hello(&WIFI_CHAT, smoke.as_bytes(), LOOPBACK_TOKEN).is_err());
        assert!(check_guest_hello(&SMOKE_TEST, wifi.as_bytes(), LOOPBACK_TOKEN).is_err());
        let later = format!("secretmessage-wifi-chat/2 {LOOPBACK_TOKEN}");
        let different = check_guest_hello(&WIFI_CHAT, later.as_bytes(), LOOPBACK_TOKEN)
            .expect_err("a guest from a later Wi-Fi test build");
        assert!(different.contains("different test APK"), "{different}");

        // The Wi-Fi build accepts encoded messages that the smoke test refuses, within its limit.
        let long = ChatPayload::Text(1, "b".repeat(MAX_TYPED_MESSAGE_BYTES * 4)).to_bytes();
        assert!(ChatPayload::parse(&long, SMOKE_TEST.max_text_bytes).is_err());
        assert!(ChatPayload::parse(&long, WIFI_CHAT.max_text_bytes).is_ok());
        let too_long = ChatPayload::Text(1, "b".repeat(WIFI_CHAT.max_text_bytes + 1)).to_bytes();
        assert!(ChatPayload::parse(&too_long, WIFI_CHAT.max_text_bytes).is_err());
        assert_eq!(WIFI_CHAT.protocol_tag(), "secretmessage-wifi-chat/1");
    }

    #[test]
    fn a_host_waiting_for_the_other_phone_stops_when_asked() {
        assert!(!super::lan_cancel_host_wait(), "no host is waiting");
        let listener =
            TcpListener::bind((Ipv4Addr::LOCALHOST, 0)).expect("bind a loopback listener");
        listener
            .set_nonblocking(true)
            .expect("use non-blocking accept with a bounded wait");
        let cancelled = Arc::new(AtomicBool::new(false));
        let flag = Arc::clone(&cancelled);
        let canceller = thread::spawn(move || {
            thread::sleep(Duration::from_millis(200));
            flag.store(true, Ordering::SeqCst);
        });
        let started = Instant::now();
        let stopped = accept_with_timeout(&listener, Duration::from_secs(20), &cancelled)
            .expect_err("the wait was stopped");
        assert!(stopped.contains("Stopped waiting"), "{stopped}");
        assert!(started.elapsed() < Duration::from_secs(5));
        canceller.join().expect("canceller thread did not panic");
    }

    /// Stands in for both apps' bridges and the relay between them, in one direction: copies
    /// what one phone's session writes to the other phone's session in uneven pieces, as
    /// WebSocket messages arrive, and passes the end of the stream on, as the app does when its
    /// WebSocket closes. Returns how many bytes it forwarded.
    fn relay_one_way(mut from: UnixStream, mut to: UnixStream) -> thread::JoinHandle<usize> {
        thread::spawn(move || {
            const PIECES: [usize; 6] = [1, 7, 300, 4096, 16 * 1024, 64 * 1024];
            let mut buffer = vec![0u8; 64 * 1024];
            let mut forwarded = 0;
            for piece in PIECES.iter().cycle() {
                match from.read(&mut buffer[..*piece]) {
                    Ok(0) | Err(_) => break,
                    Ok(read) => {
                        if to.write_all(&buffer[..read]).is_err() {
                            break;
                        }
                        forwarded += read;
                    }
                }
            }
            let _ = to.shutdown(Shutdown::Write);
            forwarded
        })
    }

    /// Both phones of the Wi-Fi chat, joined through a stand-in relay instead of a direct
    /// connection: each session gets its end of a socket pair, as from `open_relay_pipe`.
    struct Relayed {
        host: thread::JoinHandle<Seen>,
        guest: thread::JoinHandle<Seen>,
        /// The host app's end, to cut the relay as a lost connection would.
        cut: UnixStream,
        relay: [thread::JoinHandle<usize>; 2],
    }

    fn start_relayed(
        host: Side,
        host_chat: ScriptedChat,
        guest: Side,
        guest_chat: ScriptedChat,
    ) -> Relayed {
        let (host_end, host_app) = UnixStream::pair().expect("create the host's socket pair");
        let (guest_end, guest_app) = UnixStream::pair().expect("create the guest's socket pair");
        let cut = host_app.try_clone().expect("keep the host app's end");
        let relay = [
            relay_one_way(
                host_app.try_clone().expect("clone the host app's end"),
                guest_app.try_clone().expect("clone the guest app's end"),
            ),
            relay_one_way(guest_app, host_app),
        ];
        let host_identity = parse_lan_identity(&host.identity).expect("parse the host identity");
        let host_thread = thread::spawn(move || {
            let (local, _person) = host.person();
            let mut prompt = None;
            let outcome = host_session(
                &WIFI_CHAT,
                host_end,
                LOOPBACK_TOKEN,
                host_identity,
                &host.pins,
                |seen: &SafetyPrompt| {
                    prompt = shown(seen);
                    Ok(local)
                },
                host_chat,
            )
            .expect("host finished the relayed MLS exchange");
            Seen::new(outcome, prompt)
        });
        let guest_identity = parse_lan_identity(&guest.identity).expect("parse the guest identity");
        let guest_thread = thread::spawn(move || {
            let (local, _person) = guest.person();
            let mut prompt = None;
            let outcome = guest_session(
                &WIFI_CHAT,
                guest_end,
                LOOPBACK_TOKEN,
                guest_identity,
                &guest.pins,
                |seen: &SafetyPrompt| {
                    prompt = shown(seen);
                    Ok(local)
                },
                guest_chat,
            )
            .expect("guest finished the relayed MLS exchange");
            Seen::new(outcome, prompt)
        });
        Relayed {
            host: host_thread,
            guest: guest_thread,
            cut,
            relay,
        }
    }

    #[test]
    fn the_wifi_chat_runs_unchanged_through_a_relay_that_splits_the_bytes() {
        let host_identity = new_identity(HOST_DEVICE);
        let guest_identity = new_identity(GUEST_DEVICE);
        let (host_chat, host_commands, host_screen) = scripted_chat();
        let (guest_chat, guest_commands, guest_screen) = scripted_chat();
        let relayed = start_relayed(
            Side::new(&host_identity, "", true),
            host_chat,
            Side::new(&guest_identity, "", true),
            guest_chat,
        );
        assert!(matches!(next_event(&host_screen), ChatEvent::Open { .. }));
        assert!(matches!(next_event(&guest_screen), ChatEvent::Open { .. }));

        let drawing: String = (0..90_000u32)
            .map(|index| char::from(b'A' + (index % 26) as u8))
            .collect();
        guest_commands
            .send(ChatCommand::Send(1, drawing.clone()))
            .expect("queue the guest's drawing");
        assert_eq!(
            next_event(&host_screen),
            ChatEvent::Received(1, drawing.clone())
        );
        assert_eq!(next_event(&guest_screen), ChatEvent::Delivered(1));
        let reply: String = drawing.chars().rev().take(70_000).collect();
        host_commands
            .send(ChatCommand::Send(1, reply.clone()))
            .expect("queue the host's reply");
        assert_eq!(next_event(&guest_screen), ChatEvent::Received(1, reply));
        assert_eq!(next_event(&host_screen), ChatEvent::Delivered(1));

        host_commands
            .send(ChatCommand::End)
            .expect("queue the host's end of the chat");
        let ChatEvent::Ended(host_summary) = next_event(&host_screen) else {
            panic!("the host's chat did not end");
        };
        let ChatEvent::Ended(guest_summary) = next_event(&guest_screen) else {
            panic!("the guest's chat did not end");
        };
        assert!(
            host_summary.starts_with("Ended on this phone. Sent 1 (1 delivered), received 1, in "),
            "{host_summary}"
        );
        assert!(
            guest_summary
                .starts_with("Ended on the other phone. Sent 1 (1 delivered), received 1, in "),
            "{guest_summary}"
        );
        let host = relayed.host.join().expect("host thread did not panic");
        let guest = relayed.guest.join().expect("guest thread did not panic");
        assert_eq!(host.outcome.status, LAN_PASS_STATUS);
        assert_eq!(guest.outcome.status, LAN_PASS_STATUS);
        assert_eq!(host.code, guest.code);
        assert_eq!(host.peer_device, GUEST_DEVICE);
        assert_eq!(guest.peer_device, HOST_DEVICE);
        let [to_guest, to_host] = relayed.relay;
        let forwarded = to_guest.join().expect("relay thread did not panic")
            + to_host.join().expect("relay thread did not panic");
        assert!(forwarded > 160_000, "the relay carried {forwarded} bytes");
    }

    #[test]
    fn a_lost_relay_connection_stops_both_phones_with_the_connection_hint() {
        let (host_chat, _host_commands, host_screen) = scripted_chat();
        let (guest_chat, _guest_commands, guest_screen) = scripted_chat();
        let relayed = start_relayed(
            Side::new(&new_identity(HOST_DEVICE), "", true),
            host_chat,
            Side::new(&new_identity(GUEST_DEVICE), "", true),
            guest_chat,
        );
        assert!(matches!(next_event(&host_screen), ChatEvent::Open { .. }));
        assert!(matches!(next_event(&guest_screen), ChatEvent::Open { .. }));

        relayed
            .cut
            .shutdown(Shutdown::Both)
            .expect("cut the relay connection");
        for screen in [&host_screen, &guest_screen] {
            let ChatEvent::Ended(summary) = next_event(screen) else {
                panic!("the chat did not end after the relay connection was lost");
            };
            assert!(summary.starts_with("Stopped. "), "{summary}");
            assert!(summary.contains(KEEP_APP_OPEN_HINT), "{summary}");
        }
        relayed.host.join().expect("host thread did not panic");
        relayed.guest.join().expect("guest thread did not panic");
        for direction in relayed.relay {
            direction.join().expect("relay thread did not panic");
        }
    }

    #[test]
    fn a_relay_chat_needs_its_prepared_pipe_and_uses_it_once() {
        // Without a prepared pipe the chat stops before it touches the screen's state.
        let identity = new_identity(HOST_DEVICE);
        assert!(take_relay_pipe().is_err());
        for result in [
            run_relay_mls_host(&WIFI_CHAT, LOOPBACK_TOKEN, &identity, ""),
            run_relay_mls_join(&WIFI_CHAT, LOOPBACK_TOKEN, &identity, ""),
        ] {
            let error = result.expect_err("no relay pipe was prepared");
            assert!(
                error.contains("No relay connection was prepared"),
                "{error}"
            );
        }

        // Preparing again closes an unused pair, which the app then reads as ended.
        let mut replaced = open_relay_pipe().expect("prepare a relay pipe");
        let mut app_end = open_relay_pipe().expect("prepare another relay pipe");
        replaced
            .set_read_timeout(Some(Duration::from_secs(5)))
            .expect("bound the read");
        let mut byte = [0u8; 1];
        assert_eq!(replaced.read(&mut byte).expect("read the replaced pipe"), 0);

        let mut session_end = take_relay_pipe().expect("the second pipe is ready");
        assert!(
            take_relay_pipe().is_err(),
            "a pipe is used for one chat only"
        );
        session_end
            .write_all(b"x")
            .expect("write from the session end");
        app_end
            .set_read_timeout(Some(Duration::from_secs(5)))
            .expect("bound the read");
        app_end.read_exact(&mut byte).expect("read at the app end");
        assert_eq!(&byte, b"x");
    }
}
