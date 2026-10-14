#![deny(unsafe_code)]

//! Experimental OpenMLS integration spike only. This crate is not linked to
//! the Android application and does not provide application-level identity
//! verification, persistence, or a user-facing messaging API.

#[cfg(feature = "spike-harness")]
pub mod spike_harness {
    use openmls::prelude::{
        tls_codec::{Deserialize, Serialize},
        *,
    };
    use openmls_basic_credential::SignatureKeyPair;
    use openmls_rust_crypto::OpenMlsRustCrypto;

    const TEST_CIPHERSUITE: Ciphersuite = Ciphersuite::MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519;
    const ALICE_TEXT: &[u8] = b"MLS-SPIKE-ALICE-TO-BOB-TEST-PLAINTEXT-4bc91a";
    const BOB_TEXT: &[u8] = b"MLS-SPIKE-BOB-TO-ALICE-TEST-PLAINTEXT-a63fd2";

    fn test_credential(
        provider: &OpenMlsRustCrypto,
        label: &[u8],
    ) -> (CredentialWithKey, SignatureKeyPair) {
        // These BasicCredential labels are test values, not trusted identities.
        let credential = BasicCredential::new(label.to_vec());
        let signature_keys = SignatureKeyPair::new(TEST_CIPHERSUITE.signature_algorithm())
            .expect("generate test signing key");
        signature_keys
            .store(provider.storage())
            .expect("store test signing key in the provider's in-memory store");

        (
            CredentialWithKey {
                credential: credential.into(),
                signature_key: signature_keys.public().into(),
            },
            signature_keys,
        )
    }

    fn decrypt_application_message(
        recipient: &mut MlsGroup,
        provider: &OpenMlsRustCrypto,
        wire_bytes: &[u8],
    ) -> Vec<u8> {
        let incoming = MlsMessageIn::tls_deserialize_exact(wire_bytes)
            .expect("deserialize MLS message from simulated DataChannel bytes");
        let protocol_message = incoming
            .try_into_protocol_message()
            .expect("convert incoming MLS application message");
        let processed = recipient
            .process_message(provider, protocol_message)
            .expect("authenticate and process MLS message");

        match processed.into_content() {
            ProcessedMessageContent::ApplicationMessage(message) => message.into_bytes(),
            other => panic!("expected an MLS application message, got {other:?}"),
        }
    }

    /// Exercise one fixed, in-memory two-party exchange. This is build/test
    /// scaffolding only; it is not a chat API and must not be called by the app.
    pub fn run_two_member_memory_smoke_test() {
        // Separate providers model separate devices. OpenMlsRustCrypto::default()
        // uses the provider's in-memory storage; this test writes no files.
        let alice_provider = OpenMlsRustCrypto::default();
        let bob_provider = OpenMlsRustCrypto::default();
        let (alice_credential, alice_signer) = test_credential(&alice_provider, b"test-alice");
        let (bob_credential, bob_signer) = test_credential(&bob_provider, b"test-bob");

        // Bob creates a one-use KeyPackage; Alice receives only its serialized public bytes.
        let bob_key_package = KeyPackage::builder()
            .build(TEST_CIPHERSUITE, &bob_provider, &bob_signer, bob_credential)
            .expect("create Bob's test KeyPackage");
        let key_package_wire = bob_key_package
            .key_package()
            .tls_serialize_detached()
            .expect("serialize Bob's public KeyPackage");
        let key_package_in = KeyPackageIn::tls_deserialize_exact(&key_package_wire)
            .expect("deserialize Bob's public KeyPackage");
        let verified_key_package = key_package_in
            .validate(alice_provider.crypto(), ProtocolVersion::Mls10)
            .expect("validate the MLS KeyPackage structure and signature");

        // Creating a group and adding its second member are online protocol operations.
        let create_config = MlsGroupCreateConfig::builder()
            .ciphersuite(TEST_CIPHERSUITE)
            .build();
        let mut alice_group = MlsGroup::new(
            &alice_provider,
            &alice_signer,
            &create_config,
            alice_credential,
        )
        .expect("create Alice's in-memory MLS group");

        let (_commit_for_existing_members, welcome_out, _group_info) = alice_group
            .add_members(
                &alice_provider,
                &alice_signer,
                std::slice::from_ref(&verified_key_package),
            )
            .expect("add Bob to the two-member group");
        alice_group
            .merge_pending_commit(&alice_provider)
            .expect("merge Alice's pending add-member commit");

        // The Welcome is serialized just as it would be for transport. The public
        // ratchet tree is supplied separately; there is no signaling-server queue.
        let welcome_wire = welcome_out
            .tls_serialize_detached()
            .expect("serialize MLS Welcome");
        let welcome_in =
            MlsMessageIn::tls_deserialize_exact(&welcome_wire).expect("deserialize MLS Welcome");
        let welcome = match welcome_in.extract() {
            MlsMessageBodyIn::Welcome(welcome) => welcome,
            _ => panic!("expected an MLS Welcome"),
        };
        let mut bob_group = StagedWelcome::new_from_welcome(
            &bob_provider,
            create_config.join_config(),
            welcome,
            Some(alice_group.export_ratchet_tree().into()),
        )
        .expect("stage Bob's MLS group join")
        .into_group(&bob_provider)
        .expect("create Bob's in-memory MLS group");

        assert_eq!(alice_group.members().count(), 2);
        assert_eq!(bob_group.members().count(), 2);

        // Alice -> Bob. The only bytes passed to the simulated transport are the
        // serialized MLS message; the test plaintext must not appear on the wire.
        let alice_message = alice_group
            .create_message(&alice_provider, &alice_signer, ALICE_TEXT)
            .expect("encrypt Alice's test message");
        let alice_wire = alice_message
            .tls_serialize_detached()
            .expect("serialize Alice's encrypted MLS message");
        assert!(
            !alice_wire
                .windows(ALICE_TEXT.len())
                .any(|window| window == ALICE_TEXT),
            "test plaintext appeared in the serialized MLS message"
        );
        assert_eq!(
            decrypt_application_message(&mut bob_group, &bob_provider, &alice_wire),
            ALICE_TEXT
        );

        // Bob -> Alice proves the same group state can protect messages in both directions.
        let bob_message = bob_group
            .create_message(&bob_provider, &bob_signer, BOB_TEXT)
            .expect("encrypt Bob's test message");
        let bob_wire = bob_message
            .tls_serialize_detached()
            .expect("serialize Bob's encrypted MLS message");
        assert!(
            !bob_wire
                .windows(BOB_TEXT.len())
                .any(|window| window == BOB_TEXT),
            "test plaintext appeared in the serialized MLS message"
        );
        assert_eq!(
            decrypt_application_message(&mut alice_group, &alice_provider, &bob_wire),
            BOB_TEXT
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

// The single JNI symbol is isolated in a tiny module and is enabled only for the
// separate test APK. The MLS core continues to deny unsafe Rust code.
#[cfg(feature = "android-jni")]
#[allow(unsafe_code)]
mod android_jni;
