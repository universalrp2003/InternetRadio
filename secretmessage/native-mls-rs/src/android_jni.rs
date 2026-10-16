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
) -> jstring {
    unowned_env
        .with_env(|env| {
            let host_ip = host_ip.try_to_string(env)?;
            let token = token.try_to_string(env)?;
            let port = u16::try_from(port).unwrap_or(0);
            let status = match catch_unwind(AssertUnwindSafe(|| {
                crate::spike_harness::run_lan_mls_host(&host_ip, port, &token)
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
) -> jstring {
    unowned_env
        .with_env(|env| {
            let host_ip = host_ip.try_to_string(env)?;
            let token = token.try_to_string(env)?;
            let port = u16::try_from(port).unwrap_or(0);
            let status = match catch_unwind(AssertUnwindSafe(|| {
                crate::spike_harness::run_lan_mls_join(&host_ip, port, &token)
            })) {
                Ok(Ok(result)) => result,
                Ok(Err(error)) => format!("FAIL: {error}"),
                Err(_) => "FAIL: the local MLS guest test panicked.".to_owned(),
            };
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}

/// Returns the safety code waiting for this phone's user, or an empty string.
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_rpideas_secretmessage_smoke_MainActivity_nativeLanSafetyCode<
    'caller,
>(
    mut unowned_env: EnvUnowned<'caller>,
    _this: JObject<'caller>,
) -> jstring {
    unowned_env
        .with_env(|env| {
            let code = catch_unwind(crate::spike_harness::lan_safety_code)
                .ok()
                .flatten()
                .unwrap_or_default();
            env.new_string(code).map(|value| value.into_raw())
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
