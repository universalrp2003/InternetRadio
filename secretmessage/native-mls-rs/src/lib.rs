#![deny(unsafe_code)]

//! Separate feasibility harness for the published mls-rs release and its AWS-LC provider.
//! This crate is not linked into the Secret Message product application.

#[cfg(feature = "spike-harness")]
pub mod spike_harness {
    use mls_rs::client_builder::MlsConfig;
    use mls_rs::error::MlsError;
    use mls_rs::group::ReceivedMessage;
    use mls_rs::identity::basic::{BasicCredential, BasicIdentityProvider};
    use mls_rs::identity::SigningIdentity;
    use mls_rs::{
        CipherSuite, CipherSuiteProvider, Client, CryptoProvider, ExtensionList, MlsMessage,
    };
    use mls_rs_crypto_awslc::AwsLcCryptoProvider;

    const TEST_CIPHERSUITE: CipherSuite = CipherSuite::CURVE25519_AES128;
    const ALICE_TEXT: &[u8] = b"MLS-RS-SPIKE-ALICE-TO-BOB-TEST-PLAINTEXT-2f3491";
    const BOB_TEXT: &[u8] = b"MLS-RS-SPIKE-BOB-TO-ALICE-TEST-PLAINTEXT-1cda73";

    fn test_crypto_provider() -> AwsLcCryptoProvider {
        // Keep the feasibility harness on the RFC 9420 mandatory-to-implement
        // X25519/AES-128-GCM/SHA-256/Ed25519 cipher suite only.
        AwsLcCryptoProvider::with_enabled_cipher_suites(vec![TEST_CIPHERSUITE])
    }

    fn test_client(
        crypto_provider: AwsLcCryptoProvider,
        label: &str,
    ) -> Result<Client<impl MlsConfig>, MlsError> {
        let cipher_suite = crypto_provider
            .cipher_suite_provider(TEST_CIPHERSUITE)
            .expect("AWS-LC provider supports the selected test cipher suite");
        let (secret_key, public_key) = cipher_suite
            .signature_key_generate()
            .expect("generate a test-only signature key pair");

        // BasicCredential is demonstration scaffolding, not peer authentication.
        let credential = BasicCredential::new(label.as_bytes().to_vec());
        let signing_identity = SigningIdentity::new(credential.into_credential(), public_key);

        Ok(Client::builder()
            .identity_provider(BasicIdentityProvider)
            .crypto_provider(crypto_provider)
            .signing_identity(signing_identity, secret_key, TEST_CIPHERSUITE)
            .build())
    }

    fn assert_application_message(message: ReceivedMessage, expected: &[u8]) {
        match message {
            ReceivedMessage::ApplicationMessage(application) => {
                assert_eq!(application.data(), expected);
            }
            _ => panic!("expected an MLS application message"),
        }
    }

    /// Run a fixed two-member MLS exchange entirely in memory.
    ///
    /// The test serializes all protocol messages at the simulated transport
    /// boundary. It creates no identity for a real person, uses no network, and
    /// writes no persistent state.
    pub fn run_two_member_memory_smoke_test() {
        let alice = test_client(test_crypto_provider(), "test-alice")
            .expect("build Alice's test-only MLS client");
        let bob = test_client(test_crypto_provider(), "test-bob")
            .expect("build Bob's test-only MLS client");

        let mut alice_group = alice
            .create_group(ExtensionList::default(), Default::default(), None)
            .expect("create Alice's in-memory MLS group");

        // Bob's public KeyPackage crosses the simulated transport as serialized bytes.
        let bob_key_package = bob
            .generate_key_package_message(Default::default(), Default::default(), None)
            .expect("generate Bob's test KeyPackage");
        let key_package_wire = bob_key_package
            .to_bytes()
            .expect("serialize Bob's public KeyPackage");
        let key_package_in =
            MlsMessage::from_bytes(&key_package_wire).expect("deserialize Bob's public KeyPackage");

        let add_member = alice_group
            .commit_builder()
            .add_member(key_package_in)
            .expect("create the test add-member proposal");
        let commit = add_member.build().expect("build the test group commit");
        alice_group
            .apply_pending_commit()
            .expect("apply Alice's add-member commit");

        // Bob joins from the serialized Welcome, modelling the transport boundary.
        let welcome_wire = commit.welcome_messages[0]
            .to_bytes()
            .expect("serialize the MLS Welcome");
        let welcome = MlsMessage::from_bytes(&welcome_wire).expect("deserialize the MLS Welcome");
        let (mut bob_group, _) = bob
            .join_group(None, &welcome, None)
            .expect("join Bob to the in-memory MLS group");

        // Alice -> Bob: the test plaintext must not appear in the serialized MLS message.
        let alice_message = alice_group
            .encrypt_application_message(ALICE_TEXT, Vec::new())
            .expect("encrypt Alice's test message");
        let alice_wire = alice_message
            .to_bytes()
            .expect("serialize Alice's encrypted MLS message");
        assert!(
            !alice_wire
                .windows(ALICE_TEXT.len())
                .any(|window| window == ALICE_TEXT),
            "Alice's test plaintext appeared in the serialized MLS message"
        );
        let alice_in =
            MlsMessage::from_bytes(&alice_wire).expect("deserialize Alice's encrypted MLS message");
        let received_by_bob = bob_group
            .process_incoming_message(alice_in)
            .expect("authenticate and decrypt Alice's MLS message");
        assert_application_message(received_by_bob, ALICE_TEXT);

        // Bob -> Alice confirms both members can send under the resulting group state.
        let bob_message = bob_group
            .encrypt_application_message(BOB_TEXT, Vec::new())
            .expect("encrypt Bob's test message");
        let bob_wire = bob_message
            .to_bytes()
            .expect("serialize Bob's encrypted MLS message");
        assert!(
            !bob_wire
                .windows(BOB_TEXT.len())
                .any(|window| window == BOB_TEXT),
            "Bob's test plaintext appeared in the serialized MLS message"
        );
        let bob_in =
            MlsMessage::from_bytes(&bob_wire).expect("deserialize Bob's encrypted MLS message");
        let received_by_alice = alice_group
            .process_incoming_message(bob_in)
            .expect("authenticate and decrypt Bob's MLS message");
        assert_application_message(received_by_alice, BOB_TEXT);
    }

    #[cfg(test)]
    mod tests {
        use super::run_two_member_memory_smoke_test;

        #[test]
        fn two_in_memory_members_exchange_encrypted_application_messages() {
            run_two_member_memory_smoke_test();
        }
    }
}

// Reuse the existing narrowly scoped JNI method; it calls the matching
// spike_harness function above and contains no cryptographic implementation.
#[cfg(feature = "android-jni")]
#[allow(unsafe_code)]
#[path = "../../native/src/android_jni.rs"]
mod android_jni;
