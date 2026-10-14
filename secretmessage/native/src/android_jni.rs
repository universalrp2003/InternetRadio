//! Minimal JNI boundary for the separate test-only Android smoke app.
//!
//! The sole unsafe attribute exports a stable JNI symbol. No raw pointers are
//! dereferenced here; jni-rs's safe `EnvUnowned` wrapper is used for all JNI work.

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::JObject;
use jni::sys::jstring;
use jni::EnvUnowned;
use std::panic::{catch_unwind, AssertUnwindSafe};

/// Run the fixed, synthetic in-memory MLS smoke test from the standalone APK.
///
/// This is not a chat API: it accepts no user input, performs no network access,
/// persists no state, and verifies no real-world identity.
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
                Ok(()) => "PASS: two synthetic in-memory MLS members exchanged test messages; no test plaintext appeared in serialized messages.",
                Err(_) => "FAIL: the native MLS smoke test panicked; see device logs for test-only diagnostics.",
            };
            env.new_string(status).map(|value| value.into_raw())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
}
