// TARGET_BACKEND: WASM

// RUN_THIRD_PARTY_OPTIMIZER
// WASM_DCE_EXPECTED_OUTPUT_SIZE: wasm 14_851
// WASM_DCE_EXPECTED_OUTPUT_SIZE:  mjs  5_788
// WASM_OPT_EXPECTED_OUTPUT_SIZE:       4_551

// FILE: test.kt

import kotlinx.browser.document
import kotlinx.dom.appendText

@JsExport
fun test() {
    document.body?.appendText("Hello, World!")
}

// FILE: entry.mjs
import { test } from "./index.mjs"

const r = typeof test;
if (r != "function") throw Error("Wrong result: " + r);
