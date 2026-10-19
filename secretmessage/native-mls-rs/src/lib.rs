#![deny(unsafe_code)]

//! Separate feasibility harness for the published mls-rs release and its AWS-LC provider.
//! This crate is not linked into the Secret Message product application.

#[cfg(feature = "spike-harness")]
pub mod spike_harness {
    use mls_rs::client_builder::MlsConfig;
    use mls_rs::crypto::SignatureSecretKey;
    use mls_rs::error::{IntoAnyError, MlsError};
    use mls_rs::group::ReceivedMessage;
    use mls_rs::identity::basic::BasicCredential;
    use mls_rs::identity::{CredentialType, SigningIdentity};
    use mls_rs::time::MlsTime;
    use mls_rs::{
        CipherSuite, CipherSuiteProvider, Client, CryptoProvider, ExtensionList, IdentityProvider,
        MlsMessage,
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
        fn into_dyn_error(self) -> Result<Box<dyn std::error::Error + Send + Sync>, Self> {
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

    mod lan_smoke;
    pub use lan_smoke::{
        generate_lan_identity, lan_cancel_host_wait, lan_chat_end, lan_chat_poll, lan_chat_send,
        lan_safety_prompt, run_lan_mls_host, run_lan_mls_join, submit_lan_safety_decision,
        take_lan_verified_peer, LanProfile, SMOKE_TEST, WIFI_CHAT,
    };
    #[cfg(unix)]
    pub use lan_smoke::{open_relay_pipe, run_relay_mls_host, run_relay_mls_join};

    #[cfg(test)]
    mod tests {
        use super::run_two_member_memory_smoke_test;

        #[test]
        fn two_in_memory_members_exchange_encrypted_application_messages() {
            run_two_member_memory_smoke_test();
        }
    }

    /// Host-only exercise of upstream SQLCipher-backed group-state persistence.
    /// It deliberately does not demonstrate Android Keystore key protection.
    #[cfg(all(test, feature = "storage-spike"))]
    mod sqlcipher_restart_tests {
        use super::{
            assert_application_message, test_client, test_crypto_provider, test_signing_material,
            PinnedIdentityProvider, TEST_CIPHERSUITE,
        };
        use mls_rs::client_builder::{BaseSqlConfig, ClientBuilder, MlsConfig};
        use mls_rs::crypto::SignatureSecretKey;
        use mls_rs::identity::SigningIdentity;
        use mls_rs::storage_provider::sqlite::connection_strategy::{
            CipheredConnectionStrategy, FileConnectionStrategy, SqlCipherConfig, SqlCipherKey,
        };
        use mls_rs::storage_provider::sqlite::SqLiteDataStorageEngine;
        use mls_rs::{Client, ExtensionList, MlsMessage};
        use mls_rs_crypto_awslc::AwsLcCryptoProvider;
        use std::path::{Path, PathBuf};
        use std::time::{SystemTime, UNIX_EPOCH};

        const TEST_DATABASE_KEY: [u8; 32] = [0xA5; 32];
        const PERSISTENCE_SENTINEL: &[u8] = b"MLS-RS-SQLCIPHER-RESTORE-PLAINTEXT-19bd";
        const RESUMED_TEXT: &[u8] = b"MLS-RS-SQLCIPHER-RESTORED-GROUP-2fa6";

        struct TestDatabase(PathBuf);

        impl TestDatabase {
            fn new() -> Self {
                let timestamp = SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .expect("system clock is after the Unix epoch")
                    .as_nanos();
                let path = std::env::temp_dir().join(format!(
                    "mls-rs-sqlcipher-{}-{timestamp}.db",
                    std::process::id()
                ));
                Self(path)
            }

            fn path(&self) -> &Path {
                &self.0
            }
        }

        impl Drop for TestDatabase {
            fn drop(&mut self) {
                let _ = std::fs::remove_file(&self.0);
                for suffix in ["-journal", "-wal", "-shm"] {
                    let _ = std::fs::remove_file(format!("{}{suffix}", self.0.display()));
                }
            }
        }

        fn sqlcipher_client(
            path: &Path,
            database_key: [u8; 32],
            crypto_provider: AwsLcCryptoProvider,
            signing_identity: SigningIdentity,
            secret_key: SignatureSecretKey,
            identity_provider: PinnedIdentityProvider,
        ) -> Result<Client<impl MlsConfig>, Box<dyn std::error::Error>> {
            let connection = CipheredConnectionStrategy::new(
                FileConnectionStrategy::new(path),
                SqlCipherConfig::new(SqlCipherKey::RawKey(database_key)),
            );
            let storage = SqLiteDataStorageEngine::new(connection)?;
            Ok(ClientBuilder::<BaseSqlConfig>::new_sqlite(storage)?
                .identity_provider(identity_provider)
                .crypto_provider(crypto_provider)
                .signing_identity(signing_identity, secret_key, TEST_CIPHERSUITE)
                .build())
        }

        #[test]
        fn sqlcipher_group_state_reopens_and_resumes_without_plaintext_marker() {
            let database = TestDatabase::new();
            let alice_crypto = test_crypto_provider();
            let bob_crypto = test_crypto_provider();
            let (alice_identity, alice_secret_key) =
                test_signing_material(&alice_crypto, "storage-test-alice");
            let (bob_identity, bob_secret_key) =
                test_signing_material(&bob_crypto, "storage-test-bob");
            let trusted_identities = vec![alice_identity.clone(), bob_identity.clone()];

            let alice = sqlcipher_client(
                database.path(),
                TEST_DATABASE_KEY,
                alice_crypto.clone(),
                alice_identity.clone(),
                alice_secret_key.clone(),
                PinnedIdentityProvider::new(trusted_identities.clone()),
            )
            .expect("open Alice's SQLCipher-backed test client");
            let bob = test_client(
                bob_crypto,
                bob_identity,
                bob_secret_key,
                PinnedIdentityProvider::new(trusted_identities.clone()),
            )
            .expect("build Bob's in-memory test client");

            let mut alice_group = alice
                .create_group(ExtensionList::default(), Default::default(), None)
                .expect("create Alice's SQLCipher-backed group");
            let group_id = alice_group.group_id().to_vec();
            let bob_key_package = bob
                .generate_key_package_message(Default::default(), Default::default(), None)
                .expect("generate Bob's test KeyPackage");
            let bob_key_package = MlsMessage::from_bytes(
                &bob_key_package
                    .to_bytes()
                    .expect("serialize Bob's KeyPackage"),
            )
            .expect("deserialize Bob's KeyPackage");
            let add_member = alice_group
                .commit_builder()
                .add_member(bob_key_package)
                .expect("stage Bob's test KeyPackage");
            let commit = add_member.build().expect("build the MLS commit");
            alice_group
                .apply_pending_commit()
                .expect("apply Alice's post-commit group state");
            alice_group
                .write_to_storage()
                .expect("persist Alice's post-commit group state");
            let welcome = MlsMessage::from_bytes(
                &commit.welcome_messages[0]
                    .to_bytes()
                    .expect("serialize the MLS Welcome"),
            )
            .expect("deserialize the MLS Welcome");
            let (mut bob_group, _) = bob
                .join_group(None, &welcome, None)
                .expect("join Bob to the in-memory group");

            let before_restart = alice_group
                .encrypt_application_message(PERSISTENCE_SENTINEL, Vec::new())
                .expect("encrypt a marker before closing Alice's client");
            alice_group
                .write_to_storage()
                .expect("persist Alice's post-send ratchet state");
            let before_restart_wire = before_restart
                .to_bytes()
                .expect("serialize the pre-restart MLS message");
            assert!(
                !before_restart_wire
                    .windows(PERSISTENCE_SENTINEL.len())
                    .any(|window| window == PERSISTENCE_SENTINEL),
                "pre-restart plaintext marker appeared in the MLS wire message"
            );
            let received = bob_group
                .process_incoming_message(
                    MlsMessage::from_bytes(&before_restart_wire)
                        .expect("deserialize the pre-restart MLS message"),
                )
                .expect("Bob decrypts the pre-restart message");
            assert_application_message(received, PERSISTENCE_SENTINEL);

            drop(alice_group);
            drop(alice);
            let database_bytes = std::fs::read(database.path())
                .expect("SQLCipher database exists after group-state writes");
            assert!(
                !database_bytes.starts_with(b"SQLite format 3\0"),
                "SQLCipher database unexpectedly exposes the plaintext SQLite file header"
            );
            assert!(
                !database_bytes
                    .windows(PERSISTENCE_SENTINEL.len())
                    .any(|window| window == PERSISTENCE_SENTINEL),
                "test marker appeared in the encrypted SQLCipher database bytes"
            );

            let restored_alice = sqlcipher_client(
                database.path(),
                TEST_DATABASE_KEY,
                test_crypto_provider(),
                alice_identity.clone(),
                alice_secret_key.clone(),
                PinnedIdentityProvider::new(trusted_identities.clone()),
            )
            .expect("reopen Alice's SQLCipher-backed client");
            let mut restored_group = restored_alice
                .load_group(&group_id)
                .expect("reload the persisted Alice group snapshot");
            let resumed = restored_group
                .encrypt_application_message(RESUMED_TEXT, Vec::new())
                .expect("encrypt after restoring the MLS group state");
            let resumed_wire = resumed
                .to_bytes()
                .expect("serialize the post-restart MLS message");
            let received = bob_group
                .process_incoming_message(
                    MlsMessage::from_bytes(&resumed_wire)
                        .expect("deserialize the post-restart MLS message"),
                )
                .expect("Bob decrypts Alice's post-restart message");
            assert_application_message(received, RESUMED_TEXT);

            drop(restored_group);
            drop(restored_alice);
            assert!(
                sqlcipher_client(
                    database.path(),
                    [0x00; 32],
                    test_crypto_provider(),
                    alice_identity,
                    alice_secret_key,
                    PinnedIdentityProvider::new(trusted_identities),
                )
                .is_err(),
                "an incorrect SQLCipher key must not open the stored MLS state"
            );
        }
    }
}

// Narrowly scoped JNI methods for the smoke test APK and the Wi-Fi test build of the app; they
// call the matching spike_harness functions above and contain no cryptographic implementation.
#[cfg(feature = "android-jni")]
#[allow(unsafe_code)]
mod android_jni;
