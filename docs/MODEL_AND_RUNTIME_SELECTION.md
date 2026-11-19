# Model & Local Runtime Selection — DIGITAL LIFE

## Target Hardware
- **Architecture**: Android ARM64 (`arm64-v8a`)
- **SoC**: Qualcomm Snapdragon 8 Gen 3
- **GPU**: Adreno 750
- **Device**: POCO F7 Pro (Android 16)

## 1. Inference Runtime Evaluation

| Runtime | Android ARM64 | Snapdragon / GPU | RAM Overhead | Offline | License | Decision |
|---|---|---|---|---|---|---|
| **llama.cpp** | Native C++17 JNI | CPU NEON/dotprod + optional OpenCL/Vulkan | Low (~150 MB + weights) | 100% | MIT | **Selected** |
| MLC LLM | Requires TVM runtime | Adreno OpenCL | Medium-High | 100% | Apache 2.0 | Runner-up |
| Ollama | Desktop/Linux server | Not native Android APK | High | N/A | MIT | Rejected |

**Why llama.cpp**:
- Compiles cleanly into `libdigitallife_llama.so` via Android NDK + CMake.
- Supports streaming token callbacks over JNI, cancellation, configurable context (`n_ctx`), and automatic CPU fallback if GPU offload fails.

## 2. Bundled Model Evaluation

| Model | Quantization | Size | Peak RAM (4K ctx) | License | Decision |
|---|---|---|---|---|---|
| **Qwen2.5-1.5B-Instruct** | `Q4_K_M` | ~986 MB | ~1.4 GB | Apache 2.0 | **Selected** |
| Llama-3.2-1B-Instruct | `Q4_K_M` | ~750 MB | ~1.1 GB | Llama 3.2 | Viable |
| Gemma-2-2B-it | `Q4_K_M` | ~1.5 GB | ~2.1 GB | Gemma | Heavier |

## 3. Installation Size & Bundling
- **Base APK (code + llama.cpp JNI)**: ~25–35 MB
- **Bundled GGUF Weights (`qwen2.5-1.5b-instruct-q4_k_m.gguf`)**: ~986 MB
- **Total Installed Size**: ~1.02 GB

> **Note on Git & APK Packaging**: GitHub enforces a 100 MB per-file limit, so the ~986 MB `.gguf` weights file is not committed to Git. Before building a self-contained release APK, place `qwen2.5-1.5b-instruct-q4_k_m.gguf` in `app/src/main/assets/models/`. `build.gradle.kts` sets `noCompress += listOf("gguf", "bin")` so Android packages it uncompressed for direct extraction on first launch.
