//! JNI boundary for the separate mls-rs/AWS-LC test builds: the smoke test APK
//! (`com.rpideas.secretmessage.smoke.MainActivity`) and the Wi-Fi test build of the Secret
//! Message app (`com.rpideas.secretmessage.wifitest.NativeBridge`).
//!
//! It uses jni-rs safe wrappers; no raw pointers are dereferenced here. The Internet relay's
//! socket pair reaches the app as a file descriptor handed over with the safe `IntoRawFd`. The LAN entry points are
//! available only in these isolated test builds. Each export converts its arguments, calls one
//! helper below and converts the reply, so both builds get the same behaviour.

use crate::spike_harness::{LanProfile, SMOKE_TEST, WIFI_CHAT};
use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::{JObject, JString};
use jni::sys::{jint, jstring};
use jni::EnvUnowned;
#[cfg(unix)]
use std::os::fd::IntoRawFd;
use std::panic::{catch_unwind, AssertUnwindSafe};

const IN_MEMORY_PASS: &str =
    "PASS: two synthetic in-memory MLS members exchanged test messages; no test plaintext appeared in serialized messages.";
const IN_MEMORY_FAIL: &str =
    "FAIL: the native MLS smoke test panicked; see device logs for test-only diagnostics.";
/// The smoke APK's MainActivity (ANSWER_ACCEPTED) and the Wi-Fi build's session compare this exact
/// text to tell whether an answer was used, so change them together.
const ANSWER_ACCEPTED: &str = "Answer passed to the LAN test.";
const NO_COMPARISON_WAITING: &str = "No safety-code comparison is waiting.";

fn in_memory_smoke_status() -> &'static str {
    match catch_unwind(AssertUnwindSafe(|| {
        crate::spike_harness::run_two_member_memory_smoke_test();
    })) {
        Ok(()) => IN_MEMORY_PASS,
        Err(_) => IN_MEMORY_FAIL,
    }
}

fn host_status(
    profile: &LanProfile,
    host_ip: &str,
    port: jint,
    token: &str,
    identity: &str,
    pins: &str,
) -> String {
    let port = u16::try_from(port).unwrap_or(0);
    match catch_unwind(AssertUnwindSafe(|| {
        crate::spike_harness::run_lan_mls_host(profile, host_ip, port, token, identity, pins)
    })) {
        Ok(Ok(result)) => result,
        Ok(Err(error)) => format!("FAIL: {error}"),
        Err(_) => "FAIL: the local MLS host test panicked.".to_owned(),
    }
}

fn join_status(
    profile: &LanProfile,
    host_ip: &str,
    port: jint,
    token: &str,
    identity: &str,
    pins: &str,
) -> String {
    let port = u16::try_from(port).unwrap_or(0);
    match catch_unwind(AssertUnwindSafe(|| {
        crate::spike_harness::run_lan_mls_join(profile, host_ip, port, token, identity, pins)
    })) {
        Ok(Ok(result)) => result,
        Ok(Err(error)) => format!("FAIL: {error}"),
        Err(_) => "FAIL: the local MLS guest test panicked.".to_owned(),
    }
}

/// Prepares the local socket pair for one chat through the Internet relay. Returns the app's end
/// as a file descriptor that the caller now owns (the app adopts it with ParcelFileDescriptor),
/// or -1. Handing over the descriptor needs no unsafe code; the session keeps the other end.
#[cfg(unix)]
fn relay_pipe_descriptor() -> jint {
    match catch_unwind(crate::spike_harness::open_relay_pipe) {
        Ok(Ok(app_end)) => app_end.into_raw_fd(),
        _ => -1,
    }
}

#[cfg(unix)]
fn relay_host_status(profile: &LanProfile, token: &str, identity: &str, pins: &str) -> String {
    match catch_unwind(AssertUnwindSafe(|| {
        crate::spike_harness::run_relay_mls_host(profile, token, identity, pins)
    })) {
        Ok(Ok(result)) => result,
        Ok(Err(error)) => format!("FAIL: {error}"),
        Err(_) => "FAIL: the relayed MLS host panicked.".to_owned(),
    }
}

#[cfg(unix)]
fn relay_join_status(profile: &LanProfile, token: &str, identity: &str, pins: &str) -> String {
    match catch_unwind(AssertUnwindSafe(|| {
        crate::spike_harness::run_relay_mls_join(profile, token, identity, pins)
    })) {
        Ok(Ok(result)) => result,
        Ok(Err(error)) => format!("FAIL: {error}"),
        Err(_) => "FAIL: the relayed MLS guest panicked.".to_owned(),
    }
}

/// The comparison waiting for this phone's person as `code|status|device`, or "".
fn safety_prompt_text() -> String {
    catch_unwind(crate::spike_harness::lan_safety_prompt)
        .ok()
        .flatten()
        .unwrap_or_default()
}

/// Passes this phone's answer (1 = the codes match) to the waiting session.
fn submit_answer_status(codes_match: jint) -> &'static str {
    let delivered =
        catch_unwind(move || crate::spike_harness::submit_lan_safety_decision(codes_match == 1))
            .unwrap_or(false);
    if delivered {
        ANSWER_ACCEPTED
    } else {
        NO_COMPARISON_WAITING
    }
}

/// This install's persistent test identity text, or `FAIL: ...`.
fn identity_text(device_id: &str) -> String {
    match catch_unwind(AssertUnwindSafe(|| {
        crate::spike_harness::generate_lan_identity(device_id)
    })) {
        Ok(Ok(identity)) => identity,
        Ok(Err(error)) => format!("FAIL: {error}"),
        Err(_) => "FAIL: creating the test identity panicked.".to_owned(),
    }
}

/// The peer verified by this phone's last PASS (`<device ID> <key hex>`), or "".
fn verified_peer_text() -> String {
    catch_unwind(crate::spike_harness::take_lan_verified_peer)
        .ok()
        .flatten()
        .unwrap_or_default()
}

/// Queues a typed message for the open session; `OK|<number>` or `FAIL: ...`.
fn chat_send_reply(text: &str) -> String {
    match catch_unwind(AssertUnwindSafe(|| {
        crate::spike_harness::lan_chat_send(text)
    })) {
        Ok(Ok(number)) => format!("OK|{number}"),
        Ok(Err(error)) => format!("FAIL: {error}"),
        Err(_) => "FAIL: queuing the typed test message panicked.".to_owned(),
    }
}

/// The next typed-session event for the screen, or "".
fn chat_event_text() -> String {
    catch_unwind(crate::spike_harness::lan_chat_poll)
        .ok()
        .flatten()
        .unwrap_or_default()
}

fn chat_end_status() -> &'static str {
    if catch_unwind(crate::spike_harness::lan_chat_end).unwrap_or(false) {
        "Ending the typed test on both phones."
    } else {
        "No typed test is open."
    }
}

// ---- Smoke test APK: com.rpideas.secretmessage.smoke.MainActivity ----

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_runNativeSmoke<'caller>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            env.new_string(in_memory_smoke_status())
                .map(|value| value.into_raw())
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
            let status = host_status(&SMOKE_TEST, &host_ip, port, &token, &identity, &pins);
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
            let status = join_status(&SMOKE_TEST, &host_ip, port, &token, &identity, &pins);
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
            env.new_string(safety_prompt_text())
                .map(|value| value.into_raw())
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
            env.new_string(submit_answer_status(codes_match))
                .map(|value| value.into_raw())
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
            env.new_string(identity_text(&device_id))
                .map(|value| value.into_raw())
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
            env.new_string(verified_peer_text())
                .map(|value| value.into_raw())
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
            env.new_string(chat_send_reply(&text))
                .map(|value| value.into_raw())
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
            env.new_string(chat_event_text())
                .map(|value| value.into_raw())
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
            env.new_string(chat_end_status())
                .map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

// ---- Wi-Fi test build of the app: com.rpideas.secretmessage.wifitest.NativeBridge ----

/// Names the MLS library and this build's LAN protocol, so the app can show the engine loaded.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeEngineInfo<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let info = format!(
                "mls-rs 0.56.0 with AWS-LC; protocol {}",
                WIFI_CHAT.protocol_tag()
            );
            env.new_string(info).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeGenerateIdentity<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    device_id: JString<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let device_id = device_id.try_to_string(env)?;
            env.new_string(identity_text(&device_id))
                .map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeRunHost<
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
            let status = host_status(&WIFI_CHAT, &host_ip, port, &token, &identity, &pins);
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeRunJoin<
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
            let status = join_status(&WIFI_CHAT, &host_ip, port, &token, &identity, &pins);
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Prepares one chat through the Internet relay; the app's end of the local socket pair as a
/// file descriptor the app now owns, or -1.
#[cfg(unix)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeOpenRelayPipe<
    'caller,
>(
    _unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jint {
    relay_pipe_descriptor()
}

/// Hosts one chat through the Internet relay over the prepared pipe; blocks until it ends.
#[cfg(unix)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeRunRelayHost<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    token: JString<'caller>,
    identity: JString<'caller>,
    pins: JString<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let token = token.try_to_string(env)?;
            let identity = identity.try_to_string(env)?;
            let pins = pins.try_to_string(env)?;
            let status = relay_host_status(&WIFI_CHAT, &token, &identity, &pins);
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Joins one chat through the Internet relay over the prepared pipe; blocks until it ends.
#[cfg(unix)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeRunRelayJoin<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    token: JString<'caller>,
    identity: JString<'caller>,
    pins: JString<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let token = token.try_to_string(env)?;
            let identity = identity.try_to_string(env)?;
            let pins = pins.try_to_string(env)?;
            let status = relay_join_status(&WIFI_CHAT, &token, &identity, &pins);
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Stops a host that is still waiting for the other phone; 1 if one was waiting.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeCancelHostWait<
    'caller,
>(
    _unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jint {
    jint::from(catch_unwind(crate::spike_harness::lan_cancel_host_wait).unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeSafetyPrompt<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            env.new_string(safety_prompt_text())
                .map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeSubmitSafetyAnswer<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    codes_match: jint,
) -> jstring {
    unowned_env
        .with_env(|env| {
            env.new_string(submit_answer_status(codes_match))
                .map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeTakeVerifiedPeer<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            env.new_string(verified_peer_text())
                .map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeChatSend<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
    text: JString<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let text = text.try_to_string(env)?;
            env.new_string(chat_send_reply(&text))
                .map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeChatPoll<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            env.new_string(chat_event_text())
                .map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_wifitest_NativeBridge_nativeChatEnd<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            env.new_string(chat_end_status())
                .map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}
