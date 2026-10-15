#![deny(unsafe_code)]

//! Separate feasibility harness for the published mls-rs release and its AWS-LC provider.
//! This crate is not linked into the Secret Message product application.

#[cfg(feature = "spike-harness")]
pub mod spike_harness {
    use mls_rs::client_builder::MlsConfig;
    use mls_rs::crypto::{CipherSuiteProvider, SignatureSecretKey};
    use mls_rs::error::{IntoAnyError, MlsError};
    use mls_rs::group::ReceivedMessage;
    use mls_rs::identity::basic::BasicCredential;
    use mls_rs::identity::{CredentialType, SigningIdentity};
    use mls_rs::time::MlsTime;
    use mls_rs::{
        CipherSuite, Client, CryptoProvider, ExtensionList, IdentityProvider, MlsMessage,
    };
    use mls_rs_core::identity::MemberValidationContext;
    use mls_rs_crypto_awslc::AwsLcCryptoProvider;

    const TEST_CIPHERSUITE: CipherSuite = CipherSuite::CURVE25519_AES128;
    const ALICE_TAMPER_TEXT: &[u8] = b"MLS-RS-SPIKE-ALICE-TAMPER-PLAINTEXT-72af4d";
    const ALICE_TEXT: &[u8] = b"MLS-RS-SPIKE-ALICE-TO-BOB-TEST-PLAINTEXT-2f3491";
    const BOB_TAMPER_TEXT: &[u8] = b"MLS-RS-SPIKE-BOB-TAMPER-PLAINTEXT-d0ae61";
    const BOB_TEXT: &[u8] = b"MLS-RS-SPIKE-BOB-TO-ALICE-TEST-PLAINTEXT-1cda73";

    #[derive(Debug)]
    struct PinnedIdentityError;

    impl std::fmt::Display for PinnedIdentityError {
        fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
            formatter.write_str("MLS identity is not pinned by this test fixture")
        }
    }

    impl std::error::Error for PinnedIdentityError {}

    impl IntoAnyError for PinnedIdentityError {
        fn into_dyn_error(
            self,
        ) -> Result<Box<dyn std::error::Error + Send + Sync>, Self> {
            Ok(Box::new(self))
        }
    }

    /// Test-only allowlist representing an out-of-band peer-key pin.
    /// This is not a production identity or certificate system.
    #[derive(Clone, Debug)]
    struct PinnedIdentityProvider {
        trusted_identities: Vec<SigningIdentity>,
    }

    impl PinnedIdentityProvider {
        fn new(trusted_identities: Vec<SigningIdentity>) -> Self {
            Self { trusted_identities }
        }

        fn validate(&self, identity: &SigningIdentity) -> Result<(), PinnedIdentityError> {
            if self.trusted_identities.contains(identity) {
                Ok(())
            } else {
                Err(PinnedIdentityError)
            }
        }
    }

    impl IdentityProvider for PinnedIdentityProvider {
        type Error = PinnedIdentityError;

        fn validate_member(
            &self,
            signing_identity: &SigningIdentity,
            _timestamp: Option<MlsTime>,
            _context: MemberValidationContext<'_>,
        ) -> Result<(), Self::Error> {
            self.validate(signing_identity)
        }

        fn validate_external_sender(
            &self,
            signing_identity: &SigningIdentity,
            _timestamp: Option<MlsTime>,
            _extensions: Option<&ExtensionList>,
        ) -> Result<(), Self::Error> {
            self.validate(signing_identity)
        }

        fn identity(
            &self,
            signing_identity: &SigningIdentity,
            _extensions: &ExtensionList,
        ) -> Result<Vec<u8>, Self::Error> {
            self.validate(signing_identity)?;
            signing_identity
                .credential
                .as_basic()
                .map(|credential| credential.identifier().to_vec())
                .ok_or(PinnedIdentityError)
        }

        fn valid_successor(
            &self,
            predecessor: &SigningIdentity,
            successor: &SigningIdentity,
            _extensions: &ExtensionList,
        ) -> Result<bool, Self::Error> {
            self.validate(predecessor)?;
            self.validate(successor)?;
            Ok(predecessor == successor)
        }

        fn supported_types(&self) -> Vec<CredentialType> {
            vec![BasicCredential::credential_type()]
        }
    }

    fn test_crypto_provider() -> AwsLcCryptoProvider {
        // Keep the feasibility harness on the RFC 9420 mandatory-to-implement
        // X25519/AES-128-GCM/SHA-256/Ed25519 cipher suite only.
        AwsLcCryptoProvider::with_enabled_cipher_suites(vec![TEST_CIPHERSUITE])
    }

    fn test_signing_material(
        crypto_provider: &AwsLcCryptoProvider,
        label: &str,
    ) -> (SigningIdentity, SignatureSecretKey) {
        let cipher_suite = crypto_provider
            .cipher_suite_provider(TEST_CIPHERSUITE)
            .expect("AWS-LC provider supports the selected test cipher suite");
        let (secret_key, public_key) = cipher_suite
            .signature_key_generate()
            .expect("generate a test-only signature key pair");

        // BasicCredential is only a label; the fixture pins the full signing identity.
        let credential = BasicCredential::new(label.as_bytes().to_vec());
        (
            SigningIdentity::new(credential.into_credential(), public_key),
            secret_key,
        )
    }

    fn test_client(
        crypto_provider: AwsLcCryptoProvider,
        signing_identity: SigningIdentity,
        secret_key: SignatureSecretKey,
        identity_provider: PinnedIdentityProvider,
    ) -> Result<Client<impl MlsConfig>, MlsError> {
        Ok(Client::builder()
            .identity_provider(identity_provider)
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
        let alice_crypto_provider = test_crypto_provider();
        let bob_crypto_provider = test_crypto_provider();
        let (alice_identity, alice_secret_key) =
            test_signing_material(&alice_crypto_provider, "test-alice");
        let (bob_identity, bob_secret_key) =
            test_signing_material(&bob_crypto_provider, "test-bob");
        let trusted_identities = vec![alice_identity.clone(), bob_identity.clone()];

        let alice = test_client(
            alice_crypto_provider,
            alice_identity,
            alice_secret_key,
            PinnedIdentityProvider::new(trusted_identities.clone()),
        )
        .expect("build Alice's pinned test-only MLS client");
        let bob = test_client(
            bob_crypto_provider,
            bob_identity,
            bob_secret_key,
            PinnedIdentityProvider::new(trusted_identities),
        )
        .expect("build Bob's pinned test-only MLS client");

        let attacker_crypto_provider = test_crypto_provider();
        let (attacker_identity, attacker_secret_key) =
            test_signing_material(&attacker_crypto_provider, "test-bob");
        let attacker = test_client(
            attacker_crypto_provider,
            attacker_identity.clone(),
            attacker_secret_key,
            PinnedIdentityProvider::new(vec![attacker_identity]),
        )
        .expect("build the forged-peer MLS client");

        let mut alice_group = alice
            .create_group(ExtensionList::default(), Default::default(), None)
            .expect("create Alice's in-memory MLS group");

        // A different signing key reusing Bob's BasicCredential label must fail
        // Alice's exact SigningIdentity pin before the legitimate Bob is added.
        let forged_key_package = attacker
            .generate_key_package_message(Default::default(), Default::default(), None)
            .expect("generate the forged peer's test KeyPackage");
        let forged_key_package_wire = forged_key_package
            .to_bytes()
            .expect("serialize the forged peer's KeyPackage");
        let forged_key_package_in = MlsMessage::from_bytes(&forged_key_package_wire)
            .expect("deserialize the forged peer's KeyPackage");
        let forged_add = alice_group
            .commit_builder()
            .add_member(forged_key_package_in)
            .expect("stage the forged KeyPackage for rejection testing");
        assert!(
            forged_add.build().is_err(),
            "Alice must reject Bob's credential label with an unpinned signing key"
        );

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

        // Alice -> Bob: malformed ciphertext must be rejected, but a fresh
        // next-generation application message must still be accepted.
        let alice_tamper_message = alice_group
            .encrypt_application_message(ALICE_TAMPER_TEXT, Vec::new())
            .expect("encrypt Alice's tamper-test message");
        let alice_tamper_wire = alice_tamper_message
            .to_bytes()
            .expect("serialize Alice's tamper-test message");
        assert!(
            !alice_tamper_wire
                .windows(ALICE_TAMPER_TEXT.len())
                .any(|window| window == ALICE_TAMPER_TEXT),
            "Alice's tamper-test plaintext appeared in the serialized MLS message"
        );
        let mut tampered_wire = alice_tamper_wire;
        let last_byte = tampered_wire
            .last_mut()
            .expect("serialized MLS application message is non-empty");
        *last_byte ^= 0x01;
        let tampered_in = MlsMessage::from_bytes(&tampered_wire)
            .expect("ciphertext-byte change preserves the MLS message structure");
        assert!(
            bob_group.process_incoming_message(tampered_in).is_err(),
            "Bob must reject Alice's modified MLS ciphertext"
        );

        let alice_message = alice_group
            .encrypt_application_message(ALICE_TEXT, Vec::new())
            .expect("encrypt Alice's follow-up test message");
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

        // MLS must reject duplicate delivery after accepting the fresh message.
        let alice_replay =
            MlsMessage::from_bytes(&alice_wire).expect("deserialize Alice's replayed message");
        assert!(
            bob_group.process_incoming_message(alice_replay).is_err(),
            "Bob must reject a replayed MLS application message"
        );

        // Bob -> Alice mirrors the tamper, next-generation recovery and replay checks.
        let bob_tamper_message = bob_group
            .encrypt_application_message(BOB_TAMPER_TEXT, Vec::new())
            .expect("encrypt Bob's tamper-test message");
        let bob_tamper_wire = bob_tamper_message
            .to_bytes()
            .expect("serialize Bob's tamper-test message");
        assert!(
            !bob_tamper_wire
                .windows(BOB_TAMPER_TEXT.len())
                .any(|window| window == BOB_TAMPER_TEXT),
            "Bob's tamper-test plaintext appeared in the serialized MLS message"
        );
        let mut tampered_bob_wire = bob_tamper_wire;
        let last_byte = tampered_bob_wire
            .last_mut()
            .expect("serialized MLS application message is non-empty");
        *last_byte ^= 0x01;
        let tampered_bob_in = MlsMessage::from_bytes(&tampered_bob_wire)
            .expect("ciphertext-byte change preserves the MLS message structure");
        assert!(
            alice_group
                .process_incoming_message(tampered_bob_in)
                .is_err(),
            "Alice must reject Bob's modified MLS ciphertext"
        );

        let bob_message = bob_group
            .encrypt_application_message(BOB_TEXT, Vec::new())
            .expect("encrypt Bob's follow-up test message");
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

        let bob_replay =
            MlsMessage::from_bytes(&bob_wire).expect("deserialize Bob's replayed message");
        assert!(
            alice_group.process_incoming_message(bob_replay).is_err(),
            "Alice must reject a replayed MLS application message"
        );
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
