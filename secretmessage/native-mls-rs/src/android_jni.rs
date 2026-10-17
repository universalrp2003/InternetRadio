//! JNI boundary for the separate mls-rs/AWS-LC test APK.
//!
//! It uses jni-rs safe wrappers; no raw pointers are dereferenced here. The LAN
//! entry points are available only in this isolated candidate test APK.

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JObject, JString};
use jni::sys::{jint, jstring};
use jni::EnvUnowned;
use std::panic::{catch_unwind, AssertUnwindSafe};

const IN_MEMORY_PASS: &str =
    "PASS: two synthetic in-memory MLS members exchanged test messages; no test plaintext appeared in serialized messages.";
const IN_MEMORY_FAIL: &str =
    "FAIL: the native MLS smoke test panicked; see device logs for test-only diagnostics.";

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_runNativeSmoke<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let result = catch_unwind(AssertUnwindSafe(|| {
                crate::spike_harness::run_two_member_memory_smoke_test();
            }));
            let status = match result {
                Ok(()) => IN_MEMORY_PASS,
                Err(_) => IN_MEMORY_FAIL,
            };
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_runNativeLanHost<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    host_ip: JString<'caller>,
    port: jint,
    token: JString<'caller>,
    identity: JString<'caller>,
    pins: JString<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let host_ip = host_ip.try_to_string(env)?;
            let token = token.try_to_string(env)?;
            let identity = identity.try_to_string(env)?;
            let pins = pins.try_to_string(env)?;
            let port = u16::try_from(port).unwrap_or(0);
            let status = match catch_unwind(AssertUnwindSafe(|| {
                crate::spike_harness::run_lan_mls_host(&host_ip, port, &token, &identity, &pins)
            })) {
                Ok(Ok(result)) => result,
                Ok(Err(error)) => format!("FAIL: {error}"),
                Err(_) => "FAIL: the local MLS host test panicked.".to_owned(),
            };
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_runNativeLanJoin<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    host_ip: JString<'caller>,
    port: jint,
    token: JString<'caller>,
    identity: JString<'caller>,
    pins: JString<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let host_ip = host_ip.try_to_string(env)?;
            let token = token.try_to_string(env)?;
            let identity = identity.try_to_string(env)?;
            let pins = pins.try_to_string(env)?;
            let port = u16::try_from(port).unwrap_or(0);
            let status = match catch_unwind(AssertUnwindSafe(|| {
                crate::spike_harness::run_lan_mls_join(&host_ip, port, &token, &identity, &pins)
            })) {
                Ok(Ok(result)) => result,
                Ok(Err(error)) => format!("FAIL: {error}"),
                Err(_) => "FAIL: the local MLS guest test panicked.".to_owned(),
            };
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Returns the comparison waiting for this phone's user as `code|status|device`, or "".
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_nativeLanSafetyPrompt<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let prompt = catch_unwind(crate::spike_harness::lan_safety_prompt)
                .ok()
                .flatten()
                .unwrap_or_default();
            env.new_string(prompt).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Passes this phone's answer (1 = the codes match) to the waiting LAN test.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_nativeSubmitLanSafetyDecision<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    codes_match: jint,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let delivered = catch_unwind(move || {
                crate::spike_harness::submit_lan_safety_decision(codes_match == 1)
            })
            .unwrap_or(false);
            let status = if delivered {
                "Answer passed to the LAN test."
            } else {
                "No safety-code comparison is waiting."
            };
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Creates this install's persistent test identity text, or returns `FAIL: ...`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_nativeGenerateLanIdentity<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    device_id: JString<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let device_id = device_id.try_to_string(env)?;
            let identity = match catch_unwind(AssertUnwindSafe(|| {
                crate::spike_harness::generate_lan_identity(&device_id)
            })) {
                Ok(Ok(identity)) => identity,
                Ok(Err(error)) => format!("FAIL: {error}"),
                Err(_) => "FAIL: creating the test identity panicked.".to_owned(),
            };
            env.new_string(identity).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Returns the peer verified by this phone's last PASS (`<device ID> <key hex>`), or "".
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_nativeTakeLanVerifiedPeer<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let peer = catch_unwind(crate::spike_harness::take_lan_verified_peer)
                .ok()
                .flatten()
                .unwrap_or_default();
            env.new_string(peer).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Queues a typed test message for the open typed test; returns `OK|<number>` or `FAIL: ...`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_nativeLanChatSend<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    text: JString<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let text = text.try_to_string(env)?;
            let reply = match catch_unwind(AssertUnwindSafe(|| {
                crate::spike_harness::lan_chat_send(&text)
            })) {
                Ok(Ok(number)) => format!("OK|{number}"),
                Ok(Err(error)) => format!("FAIL: {error}"),
                Err(_) => "FAIL: queuing the typed test message panicked.".to_owned(),
            };
            env.new_string(reply).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Returns the next typed-test event for the screen, or "".
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_nativeLanChatPoll<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let event = catch_unwind(crate::spike_harness::lan_chat_poll)
                .ok()
                .flatten()
                .unwrap_or_default();
            env.new_string(event).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Asks the open typed test to end for both phones.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_nativeLanChatEnd<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let ending = catch_unwind(crate::spike_harness::lan_chat_end).unwrap_or(false);
            let status = if ending {
                "Ending the typed test on both phones."
            } else {
                "No typed test is open."
            };
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}
