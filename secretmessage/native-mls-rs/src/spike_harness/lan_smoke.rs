//! Test-only direct-LAN MLS exchange used by the standalone Android smoke APK.
//!
//! Each test install keeps one MLS signing identity, which the app stores wrapped with an Android
//! Keystore key; the MLS layer accepts any BasicCredential. Before any test message is exchanged,
//! both people compare a safety code derived from the RFC 9420 epoch authenticator of their shared
//! group and confirm it on both phones; a relaying device would hold two different groups, so the
//! phones would show different codes. After a confirmed session each phone remembers the other
//! phone's key and warns if that device later presents a different key. This tests those
//! verification steps. It is not a production trust policy, a chat implementation, or suitable
//! for real/private messages.

use super::{test_crypto_provider, test_signing_material, TEST_CIPHERSUITE};
use mls_rs::client_builder::MlsConfig;
use mls_rs::crypto::{SignaturePublicKey, SignatureSecretKey};
use mls_rs::group::ReceivedMessage;
use mls_rs::identity::basic::{BasicCredential, BasicIdentityProvider};
use mls_rs::identity::SigningIdentity;
use mls_rs::{Client, ExtensionList, MlsMessage};
use mls_rs_crypto_awslc::AwsLcCryptoProvider;
use std::io::{Read, Write};
use std::net::{Ipv4Addr, SocketAddr, SocketAddrV4, TcpListener, TcpStream};
use std::sync::mpsc::{self, Sender};
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
const LAN_PASS_STATUS: &str =
    "PASS: both people confirmed the same MLS safety code, then two separate devices exchanged fixed MLS test messages directly over local Wi-Fi. This phone now remembers the other phone's test key. No user text was sent or stored. TEST ONLY.";
const LOCAL_REJECTED_STATUS: &str =
    "STOPPED: you reported that the safety codes differ, so no test messages were exchanged. If the two phones really showed different codes, another device may have intercepted the connection. TEST ONLY.";
const PEER_REJECTED_STATUS: &str =
    "STOPPED: the other phone reported that the safety codes differ, so no test messages were exchanged. TEST ONLY.";

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

/// Offer the comparison to the test APK's screen and wait for this phone's user to answer.
fn ask_local_user(prompt: &SafetyPrompt) -> Result<bool, String> {
    let (answer, response) = mpsc::channel();
    {
        let mut pending = PENDING_VERIFICATION
            .lock()
            .map_err(|_| "The safety-code prompt is unavailable.".to_owned())?;
        *pending = Some(PendingVerification {
            screen_text: prompt.screen_text(),
            answer,
        });
    }
    let result = response.recv_timeout(VERIFY_TIMEOUT);
    if let Ok(mut pending) = PENDING_VERIFICATION.lock() {
        *pending = None;
    }
    result.map_err(|_| {
        "No safety-code answer was given within two minutes, so no test messages were exchanged."
            .to_owned()
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

fn accept_with_timeout(listener: &TcpListener, wait: Duration) -> Result<TcpStream, String> {
    let deadline = Instant::now() + wait;
    loop {
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

fn configure_stream(stream: &TcpStream) -> Result<(), String> {
    stream
        .set_read_timeout(Some(IO_TIMEOUT))
        .and_then(|_| stream.set_write_timeout(Some(IO_TIMEOUT)))
        .and_then(|_| stream.set_nodelay(true))
        .map_err(|error| format!("Could not configure the LAN test socket: {error}"))
}

fn set_read_timeout(stream: &TcpStream, timeout: Duration) -> Result<(), String> {
    stream
        .set_read_timeout(Some(timeout))
        .map_err(|error| format!("Could not configure the LAN test socket: {error}"))
}

fn send_frame(stream: &mut TcpStream, bytes: &[u8]) -> Result<(), String> {
    if bytes.len() > MAX_FRAME_BYTES {
        return Err("Refusing an oversized test frame.".into());
    }
    let length =
        u32::try_from(bytes.len()).map_err(|_| "Test frame length overflowed.".to_owned())?;
    stream
        .write_all(&length.to_be_bytes())
        .and_then(|_| stream.write_all(bytes))
        .and_then(|_| stream.flush())
        .map_err(|error| format!("Could not send the LAN test frame: {error}"))
}

fn receive_frame(stream: &mut TcpStream) -> Result<Vec<u8>, String> {
    let mut length_bytes = [0u8; 4];
    stream
        .read_exact(&mut length_bytes)
        .map_err(|error| format!("Could not read the LAN test frame length: {error}"))?;
    let length = u32::from_be_bytes(length_bytes) as usize;
    if length > MAX_FRAME_BYTES {
        return Err("Peer sent an oversized test frame.".into());
    }
    let mut bytes = vec![0u8; length];
    stream
        .read_exact(&mut bytes)
        .map_err(|error| format!("Could not read the complete LAN test frame: {error}"))?;
    Ok(bytes)
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

/// Ask this phone's user to compare the safety code, then exchange both answers inside MLS.
///
/// Returns a STOPPED status if either person reported different codes. Each phone always reads
/// the other answer before going on or closing, so neither resets the connection while the other
/// person is still comparing.
fn verify_safety_code<C, D>(
    stream: &mut TcpStream,
    group: &mut mls_rs::group::Group<C>,
    prompt: &SafetyPrompt,
    decide: D,
) -> Result<Option<String>, String>
where
    C: MlsConfig,
    D: FnOnce(&SafetyPrompt) -> Result<bool, String>,
{
    let codes_match = decide(prompt)?;
    let answer = if codes_match {
        CODE_CONFIRMED
    } else {
        CODE_REJECTED
    };
    let answer_wire = encrypt_test_message(group, answer, "safety-code answer")?;
    send_frame(stream, &answer_wire)?;

    set_read_timeout(stream, VERIFY_TIMEOUT + IO_TIMEOUT)?;
    let peer_wire = receive_frame(stream)?;
    set_read_timeout(stream, IO_TIMEOUT)?;
    let peer_answer = parse_mls_message(&peer_wire, "peer safety-code answer")?;
    let received = group
        .process_incoming_message(peer_answer)
        .map_err(|error| format!("Rejected the peer's MLS safety-code answer: {error}"))?;
    let peer_confirmed = match received {
        ReceivedMessage::ApplicationMessage(application)
            if application.data() == CODE_CONFIRMED =>
        {
            true
        }
        ReceivedMessage::ApplicationMessage(application) if application.data() == CODE_REJECTED => {
            false
        }
        _ => return Err("The peer's safety-code answer was not recognized.".into()),
    };
    if !codes_match {
        Ok(Some(LOCAL_REJECTED_STATUS.into()))
    } else if peer_confirmed {
        Ok(None)
    } else {
        Ok(Some(PEER_REJECTED_STATUS.into()))
    }
}

fn host_session<D>(
    mut stream: TcpStream,
    token: &str,
    identity: LanIdentity,
    pins: &str,
    decide: D,
) -> Result<SessionOutcome, String>
where
    D: FnOnce(&SafetyPrompt) -> Result<bool, String>,
{
    configure_stream(&stream)?;

    let supplied_token = receive_frame(&mut stream)?;
    if supplied_token.as_slice() != token.as_bytes() {
        return Err("The invite does not match the active host session.".into());
    }

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

    Ok(SessionOutcome {
        status: LAN_PASS_STATUS.into(),
        verified_peer: Some(pin),
    })
}

fn guest_session<D>(
    mut stream: TcpStream,
    token: &str,
    identity: LanIdentity,
    pins: &str,
    decide: D,
) -> Result<SessionOutcome, String>
where
    D: FnOnce(&SafetyPrompt) -> Result<bool, String>,
{
    configure_stream(&stream)?;
    send_frame(&mut stream, token.as_bytes())?;

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
/// The listener closes when one test completes or after three minutes.
pub fn run_lan_mls_host(
    host_ip: &str,
    port: u16,
    token: &str,
    identity: &str,
    pins: &str,
) -> Result<String, String> {
    set_verified_peer(None);
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
    let stream = accept_with_timeout(&listener, HOST_WAIT)?;
    host_session(stream, token, identity, pins, ask_local_user).map(finish_session)
}

/// Join a one-peer test host on a private LAN IPv4 address with this install's identity.
pub fn run_lan_mls_join(
    host_ip: &str,
    port: u16,
    token: &str,
    identity: &str,
    pins: &str,
) -> Result<String, String> {
    set_verified_peer(None);
    validate_invite_token(token)?;
    let identity = parse_lan_identity(identity)?;
    let host_ip = require_private_ipv4(host_ip)?;
    if port == 0 {
        return Err("A valid non-zero local test port is required.".into());
    }
    let address = SocketAddr::V4(SocketAddrV4::new(host_ip, port));
    let stream = TcpStream::connect_timeout(&address, CONNECT_TIMEOUT)
        .map_err(|error| format!("Could not connect to the local Wi-Fi test host: {error}"))?;
    guest_session(stream, token, identity, pins, ask_local_user).map(finish_session)
}

#[cfg(test)]
mod tests {
    use super::{
        ask_local_user, classify_peer, from_hex, generate_lan_identity, group_safety_code,
        guest_session, host_session, identity_client, lan_safety_prompt, parse_lan_identity,
        peer_identity, safety_code_from_authenticator, submit_lan_safety_decision, to_hex,
        PeerStatus, SafetyPrompt, SessionOutcome, LAN_PASS_STATUS, LOCAL_REJECTED_STATUS,
        PEER_REJECTED_STATUS,
    };
    use mls_rs::client_builder::MlsConfig;
    use mls_rs::group::Group;
    use mls_rs::{Client, ExtensionList, MlsMessage};
    use std::net::{Ipv4Addr, TcpListener, TcpStream};
    use std::thread;
    use std::time::{Duration, Instant};

    const LOOPBACK_TOKEN: &str = "0123456789abcdef0123456789abcdef";
    const HOST_DEVICE: &str = "00112233445566aa";
    const GUEST_DEVICE: &str = "8899aabbccddeeff";

    /// One phone in a loopback run: its identity text, remembered peers and scripted answer.
    struct Side {
        identity: String,
        pins: String,
        answer: bool,
    }

    impl Side {
        fn new(identity: &str, pins: &str, answer: bool) -> Self {
            Self {
                identity: identity.to_owned(),
                pins: pins.to_owned(),
                answer,
            }
        }
    }

    /// What one phone showed and reported in a loopback run.
    struct Seen {
        outcome: SessionOutcome,
        code: String,
        peer_status: PeerStatus,
        peer_device: String,
    }

    impl Seen {
        fn new(outcome: SessionOutcome, prompt: Option<(String, PeerStatus, String)>) -> Self {
            let (code, peer_status, peer_device) = prompt.expect("the phone showed a safety code");
            Self {
                outcome,
                code,
                peer_status,
                peer_device,
            }
        }
    }

    fn shown(prompt: &SafetyPrompt) -> Option<(String, PeerStatus, String)> {
        Some((
            prompt.code.clone(),
            prompt.peer_status,
            prompt.peer_device.clone(),
        ))
    }

    fn run_loopback(host: Side, guest: Side) -> (Seen, Seen) {
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
            let mut prompt = None;
            let outcome = host_session(
                stream,
                LOOPBACK_TOKEN,
                host_identity,
                &host.pins,
                |seen: &SafetyPrompt| {
                    prompt = shown(seen);
                    Ok(host.answer)
                },
            )
            .expect("host finished the MLS LAN exchange");
            Seen::new(outcome, prompt)
        });

        let guest_identity = parse_lan_identity(&guest.identity).expect("parse the guest identity");
        let stream = TcpStream::connect_timeout(&address, Duration::from_secs(5))
            .expect("connect the independent guest to the local test host");
        let mut prompt = None;
        let outcome = guest_session(
            stream,
            LOOPBACK_TOKEN,
            guest_identity,
            &guest.pins,
            |seen: &SafetyPrompt| {
                prompt = shown(seen);
                Ok(guest.answer)
            },
        )
        .expect("guest finished the MLS LAN exchange");
        let guest_seen = Seen::new(outcome, prompt);
        let host_seen = host_thread.join().expect("host thread did not panic");
        (host_seen, guest_seen)
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

        let asking = thread::spawn(|| {
            ask_local_user(&SafetyPrompt {
                code: "12345 67890 13579 24680".into(),
                peer_device: GUEST_DEVICE.into(),
                peer_status: PeerStatus::KeyChanged,
            })
        });
        let deadline = Instant::now() + Duration::from_secs(5);
        while lan_safety_prompt().is_none() {
            assert!(
                Instant::now() < deadline,
                "the prompt never reached the screen"
            );
            thread::sleep(Duration::from_millis(10));
        }
        assert_eq!(
            lan_safety_prompt().as_deref(),
            Some("12345 67890 13579 24680|CHANGED|8899aabbccddeeff")
        );
        assert!(submit_lan_safety_decision(true));
        assert_eq!(
            asking.join().expect("the prompt thread did not panic"),
            Ok(true)
        );
        assert_eq!(lan_safety_prompt(), None, "an answered prompt is cleared");
        assert!(
            !submit_lan_safety_decision(false),
            "an answer is accepted only once"
        );
    }

    #[test]
    fn rejects_non_private_hosts_and_malformed_invites() {
        assert!(super::require_private_ipv4("203.0.113.5").is_err());
        assert!(super::require_private_ipv4("192.168.1.5").is_ok());
        assert!(super::validate_invite_token("short").is_err());
    }
}
