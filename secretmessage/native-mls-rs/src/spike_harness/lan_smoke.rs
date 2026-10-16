//! Test-only direct-LAN MLS exchange used by the standalone Android smoke APK.
//!
//! The phones use ephemeral identities, and the MLS layer accepts any BasicCredential. Before any
//! test message is exchanged, both people compare a safety code derived from the RFC 9420 epoch
//! authenticator of their shared group and confirm it on both phones; a relaying device would hold
//! two different groups, so the phones would show different codes. This tests that verification
//! step. It is not a production trust policy, a chat implementation, or suitable for real/private
//! messages.

use super::{test_crypto_provider, test_signing_material, TEST_CIPHERSUITE};
use mls_rs::client_builder::MlsConfig;
use mls_rs::crypto::SignatureSecretKey;
use mls_rs::group::ReceivedMessage;
use mls_rs::identity::basic::BasicIdentityProvider;
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
const GUEST_TO_HOST: &[u8] = b"MLS-LAN-SMOKE-GUEST-TO-HOST-7fa19c";
const HOST_TO_GUEST: &[u8] = b"MLS-LAN-SMOKE-HOST-TO-GUEST-2ce840";
const GUEST_ACK: &[u8] = b"MLS-LAN-SMOKE-GUEST-ACK-55b0e2";
const CODE_CONFIRMED: &[u8] = b"MLS-LAN-SAFETY-CODE-CONFIRMED-3b9e41";
const CODE_REJECTED: &[u8] = b"MLS-LAN-SAFETY-CODE-REJECTED-c47d02";
const LAN_PASS_STATUS: &str =
    "PASS: both people confirmed the same MLS safety code, then two separate devices exchanged fixed MLS test messages directly over local Wi-Fi; no user text was sent or stored. TEST ONLY.";
const LOCAL_REJECTED_STATUS: &str =
    "STOPPED: you reported that the safety codes differ, so no test messages were exchanged. If the two phones really showed different codes, another device may have intercepted the connection. TEST ONLY.";
const PEER_REJECTED_STATUS: &str =
    "STOPPED: the other phone reported that the safety codes differ, so no test messages were exchanged. TEST ONLY.";

/// The safety code this phone's user is asked to compare, and where their answer goes.
struct PendingVerification {
    code: String,
    answer: Sender<bool>,
}

static PENDING_VERIFICATION: Mutex<Option<PendingVerification>> = Mutex::new(None);

/// The safety code waiting for this phone's user, if a LAN test is at the comparison step.
pub fn lan_safety_code() -> Option<String> {
    let pending = PENDING_VERIFICATION.lock().ok()?;
    pending
        .as_ref()
        .map(|verification| verification.code.clone())
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

/// Offer the code to the test APK's screen and wait for this phone's user to answer.
fn ask_local_user(code: &str) -> Result<bool, String> {
    let (answer, response) = mpsc::channel();
    {
        let mut pending = PENDING_VERIFICATION
            .lock()
            .map_err(|_| "The safety-code prompt is unavailable.".to_owned())?;
        *pending = Some(PendingVerification {
            code: code.to_owned(),
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
    decide: D,
) -> Result<Option<String>, String>
where
    C: MlsConfig,
    D: FnOnce(&str) -> Result<bool, String>,
{
    let code = group_safety_code(group)?;
    let codes_match = decide(&code)?;
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

fn host_session<D>(mut stream: TcpStream, token: &str, decide: D) -> Result<String, String>
where
    D: FnOnce(&str) -> Result<bool, String>,
{
    configure_stream(&stream)?;

    let supplied_token = receive_frame(&mut stream)?;
    if supplied_token.as_slice() != token.as_bytes() {
        return Err("The invite does not match the active host session.".into());
    }

    let guest_key_package_wire = receive_frame(&mut stream)?;
    let guest_key_package = parse_mls_message(&guest_key_package_wire, "guest MLS KeyPackage")?;

    let host_crypto = test_crypto_provider();
    let (host_identity, host_secret) = test_signing_material(&host_crypto, "lan-test-host");
    let host_client = test_peer_client(host_crypto, host_identity, host_secret)
        .map_err(|error| format!("Could not create the test host MLS client: {error}"))?;
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

    if let Some(stopped) = verify_safety_code(&mut stream, &mut host_group, decide)? {
        return Ok(stopped);
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

    Ok(LAN_PASS_STATUS.into())
}

fn guest_session<D>(mut stream: TcpStream, token: &str, decide: D) -> Result<String, String>
where
    D: FnOnce(&str) -> Result<bool, String>,
{
    configure_stream(&stream)?;
    send_frame(&mut stream, token.as_bytes())?;

    let guest_crypto = test_crypto_provider();
    let (guest_identity, guest_secret) = test_signing_material(&guest_crypto, "lan-test-guest");
    let guest_client = test_peer_client(guest_crypto, guest_identity, guest_secret)
        .map_err(|error| format!("Could not create the test guest MLS client: {error}"))?;
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

    if let Some(stopped) = verify_safety_code(&mut stream, &mut guest_group, decide)? {
        return Ok(stopped);
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

    Ok(LAN_PASS_STATUS.into())
}

/// Start a one-peer, ephemeral MLS test host on a private IPv4 address.
/// The listener closes when one test completes or after three minutes.
pub fn run_lan_mls_host(host_ip: &str, port: u16, token: &str) -> Result<String, String> {
    validate_invite_token(token)?;
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
    host_session(stream, token, ask_local_user)
}

/// Join a one-peer test host only when its address is a private LAN IPv4 address.
pub fn run_lan_mls_join(host_ip: &str, port: u16, token: &str) -> Result<String, String> {
    validate_invite_token(token)?;
    let host_ip = require_private_ipv4(host_ip)?;
    if port == 0 {
        return Err("A valid non-zero local test port is required.".into());
    }
    let address = SocketAddr::V4(SocketAddrV4::new(host_ip, port));
    let stream = TcpStream::connect_timeout(&address, CONNECT_TIMEOUT)
        .map_err(|error| format!("Could not connect to the local Wi-Fi test host: {error}"))?;
    guest_session(stream, token, ask_local_user)
}

#[cfg(test)]
mod tests {
    use super::{
        ask_local_user, group_safety_code, guest_session, host_session, lan_safety_code,
        safety_code_from_authenticator, submit_lan_safety_decision, test_crypto_provider,
        test_peer_client, test_signing_material, LAN_PASS_STATUS, LOCAL_REJECTED_STATUS,
        PEER_REJECTED_STATUS,
    };
    use mls_rs::client_builder::MlsConfig;
    use mls_rs::group::Group;
    use mls_rs::{Client, ExtensionList, MlsMessage};
    use std::net::{Ipv4Addr, TcpListener, TcpStream};
    use std::sync::mpsc;
    use std::thread;
    use std::time::{Duration, Instant};

    const LOOPBACK_TOKEN: &str = "0123456789abcdef0123456789abcdef";

    /// Results and displayed codes from one host-loopback run with scripted answers.
    struct LoopbackRun {
        host_result: String,
        guest_result: String,
        host_code: String,
        guest_code: String,
    }

    fn run_loopback(host_answer: bool, guest_answer: bool) -> LoopbackRun {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0))
            .expect("bind a host-only loopback MLS test listener");
        listener
            .set_nonblocking(true)
            .expect("use non-blocking accept with a bounded wait");
        let address = listener.local_addr().expect("read the loopback address");
        let (host_code_sender, host_code_receiver) = mpsc::channel();
        let host = thread::spawn(move || {
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
            host_session(stream, LOOPBACK_TOKEN, move |code: &str| {
                host_code_sender
                    .send(code.to_owned())
                    .expect("record the host's safety code");
                Ok(host_answer)
            })
            .expect("host finished the MLS LAN exchange")
        });

        let stream = TcpStream::connect_timeout(&address, Duration::from_secs(5))
            .expect("connect the independent guest to the local test host");
        let mut guest_code = String::new();
        let guest_result = guest_session(stream, LOOPBACK_TOKEN, |code: &str| {
            guest_code = code.to_owned();
            Ok(guest_answer)
        })
        .expect("guest finished the MLS LAN exchange");
        let host_result = host.join().expect("host thread did not panic");
        let host_code = host_code_receiver
            .recv()
            .expect("the host showed a safety code");
        LoopbackRun {
            host_result,
            guest_result,
            host_code,
            guest_code,
        }
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

    fn peer(label: &str) -> Client<impl MlsConfig> {
        let crypto = test_crypto_provider();
        let (identity, secret) = test_signing_material(&crypto, label);
        test_peer_client(crypto, identity, secret).expect("build a test-only MLS client")
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
    fn confirmed_safety_codes_let_two_independent_clients_exchange_fixed_messages() {
        let run = run_loopback(true, true);
        assert_eq!(run.host_result, LAN_PASS_STATUS);
        assert_eq!(run.guest_result, LAN_PASS_STATUS);
        assert_eq!(
            run.host_code, run.guest_code,
            "both phones must show the same safety code"
        );
        assert_safety_code_format(&run.host_code);
    }

    #[test]
    fn a_reported_mismatch_stops_both_phones_before_any_test_message() {
        let guest_rejects = run_loopback(true, false);
        assert_eq!(guest_rejects.guest_result, LOCAL_REJECTED_STATUS);
        assert_eq!(guest_rejects.host_result, PEER_REJECTED_STATUS);
        assert_eq!(guest_rejects.host_code, guest_rejects.guest_code);

        let host_rejects = run_loopback(false, true);
        assert_eq!(host_rejects.host_result, LOCAL_REJECTED_STATUS);
        assert_eq!(host_rejects.guest_result, PEER_REJECTED_STATUS);

        let both_reject = run_loopback(false, false);
        assert_eq!(both_reject.host_result, LOCAL_REJECTED_STATUS);
        assert_eq!(both_reject.guest_result, LOCAL_REJECTED_STATUS);
    }

    #[test]
    fn a_relaying_device_cannot_make_both_phones_show_the_same_code() {
        // A device in the middle must join the real host's group as its guest and invite the
        // real guest into a second group of its own.
        let host = peer("lan-test-host");
        let relay_posing_as_guest = peer("lan-test-guest");
        let relay_posing_as_host = peer("lan-test-host");
        let guest = peer("lan-test-guest");

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
    }

    #[test]
    fn the_screen_receives_the_code_and_returns_the_answer() {
        assert_eq!(lan_safety_code(), None);
        assert!(!submit_lan_safety_decision(true), "nothing is waiting yet");

        let asking = thread::spawn(|| ask_local_user("12345 67890 13579 24680"));
        let deadline = Instant::now() + Duration::from_secs(5);
        while lan_safety_code().is_none() {
            assert!(
                Instant::now() < deadline,
                "the code never reached the screen"
            );
            thread::sleep(Duration::from_millis(10));
        }
        assert_eq!(
            lan_safety_code().as_deref(),
            Some("12345 67890 13579 24680")
        );
        assert!(submit_lan_safety_decision(true));
        assert_eq!(
            asking.join().expect("the prompt thread did not panic"),
            Ok(true)
        );
        assert_eq!(lan_safety_code(), None, "an answered prompt is cleared");
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
