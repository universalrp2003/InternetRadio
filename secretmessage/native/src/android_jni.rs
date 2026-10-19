//! Minimal JNI boundary for the separate test-only Android smoke app.
//!
//! This OpenMLS candidate exposes only the in-memory smoke method. The MLS core
//! continues to deny unsafe Rust code; the wrapper uses jni-rs safe APIs.

use jni::errors::ThrowRuntimeExAndDefault;
use jni::objects::JObject;
use jni::sys::jstring;
use jni::EnvUnowned;
use std::panic::{catch_unwind, AssertUnwindSafe};

const IN_MEMORY_PASS: &str =
    "PASS: two synthetic in-memory MLS members exchanged test messages; no test plaintext appeared in serialized messages.";
const IN_MEMORY_FAIL: &str =
    "FAIL: the native MLS smoke test panicked; see device logs for test-only diagnostics.";

/// Run the fixed synthetic in-memory MLS test. It accepts no user input, performs
/// no network access, persists no state, and is not a chat API.
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
