//! Test-only direct-LAN MLS exchange used by the standalone Android smoke APK.
//!
//! This deliberately accepts any BasicCredential and uses ephemeral identities. It is not a
//! production trust policy, a chat implementation, or suitable for real/private messages.

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
use std::thread;
use std::time::{Duration, Instant};

const MAX_FRAME_BYTES: usize = 1024 * 1024;
const HOST_WAIT: Duration = Duration::from_secs(180);
const CONNECT_TIMEOUT: Duration = Duration::from_secs(15);
const IO_TIMEOUT: Duration = Duration::from_secs(45);
const GUEST_TO_HOST: &[u8] = b"MLS-LAN-SMOKE-GUEST-TO-HOST-7fa19c";
const HOST_TO_GUEST: &[u8] = b"MLS-LAN-SMOKE-HOST-TO-GUEST-2ce840";
const GUEST_ACK: &[u8] = b"MLS-LAN-SMOKE-GUEST-ACK-55b0e2";
const LAN_PASS_STATUS: &str =
    "PASS: two separate devices exchanged fixed MLS test messages directly over local Wi-Fi; no user text was sent or stored. TEST ONLY.";

fn test_peer_client(
    crypto_provider: AwsLcCryptoProvider,
    signing_identity: SigningIdentity,
    secret_key: SignatureSecretKey,
) -> Result<Client<impl MlsConfig>, mls_rs::error::MlsError> {
    // mls-rs documents BasicIdentityProvider as always-valid for BasicCredential and
    // recommends it only for tests. Real identity verification is deliberately absent here.
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

fn send_frame(stream: &mut TcpStream, bytes: &[u8]) -> Result<(), String> {
    if bytes.len() > MAX_FRAME_BYTES {
        return Err("Refusing an oversized test frame.".into());
    }
    let length = u32::try_from(bytes.len())
        .map_err(|_| "Test frame length overflowed.".to_owned())?;
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
        ReceivedMessage::ApplicationMessage(application)
            if application.data() == expected =>
        {
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
        return Err(format!("The fixed plaintext marker appeared in {context} wire bytes."));
    }
    Ok(wire)
}

fn host_session(mut stream: TcpStream, token: &str) -> Result<String, String> {
    stream
        .set_read_timeout(Some(IO_TIMEOUT))
        .and_then(|_| stream.set_write_timeout(Some(IO_TIMEOUT)))
        .and_then(|_| stream.set_nodelay(true))
        .map_err(|error| format!("Could not configure the LAN test socket: {error}"))?;

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

fn guest_session(mut stream: TcpStream, token: &str) -> Result<String, String> {
    stream
        .set_read_timeout(Some(IO_TIMEOUT))
        .and_then(|_| stream.set_write_timeout(Some(IO_TIMEOUT)))
        .and_then(|_| stream.set_nodelay(true))
        .map_err(|error| format!("Could not configure the LAN test socket: {error}"))?;
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

    let guest_wire = encrypt_test_message(
        &mut guest_group,
        GUEST_TO_HOST,
        "guest-to-host message",
    )?;
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
    host_session(stream, token)
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
    guest_session(stream, token)
}

#[cfg(test)]
mod tests {
    use super::{host_session, guest_session};
    use std::net::{Ipv4Addr, TcpListener, TcpStream};
    use std::thread;
    use std::time::Duration;

    const LOOPBACK_TOKEN: &str = "0123456789abcdef0123456789abcdef";

    #[test]
    fn two_independent_mls_clients_exchange_fixed_messages_over_tcp() {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0))
            .expect("bind a host-only loopback MLS test listener");
        listener
            .set_nonblocking(true)
            .expect("use non-blocking accept with a bounded wait");
        let address = listener.local_addr().expect("read the loopback address");
        let host = thread::spawn(move || {
            let deadline = std::time::Instant::now() + Duration::from_secs(20);
            let stream = loop {
                match listener.accept() {
                    Ok((stream, _)) => break stream,
                    Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                        assert!(
                            std::time::Instant::now() < deadline,
                            "test guest did not connect"
                        );
                        thread::sleep(Duration::from_millis(10));
                    }
                    Err(error) => panic!("loopback accept failed: {error}"),
                }
            };
            host_session(stream, LOOPBACK_TOKEN).expect("host completed the MLS LAN exchange")
        });

        let stream = TcpStream::connect_timeout(&address, Duration::from_secs(5))
            .expect("connect the independent guest to the local test host");
        let guest_result =
            guest_session(stream, LOOPBACK_TOKEN).expect("guest completed MLS exchange");
        let host_result = host.join().expect("host thread did not panic");
        assert!(host_result.starts_with("PASS:"), "{host_result}");
        assert!(guest_result.starts_with("PASS:"), "{guest_result}");
    }

    #[test]
    fn rejects_non_private_hosts_and_malformed_invites() {
        assert!(super::require_private_ipv4("203.0.113.5").is_err());
        assert!(super::require_private_ipv4("192.168.1.5").is_ok());
        assert!(super::validate_invite_token("short").is_err());
    }
}
