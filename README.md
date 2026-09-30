# Hindi Voicemail v0.2.0-alpha

Colourful dark-blue/teal interface with English controls. The bundled greeting and offline speech recognition remain Hindi. This is an experimental microphone demo and audio transcription app, not live SIM call conversation. Not tested on Redmi Note 10.

The APK is debug-signed. Android may require uninstalling the old build before installing this one if signing keys differ. Uninstalling removes the local inbox; share any important transcripts first.

# Hindi Voicemail (experimental v0.1)

A free, offline Hindi voice-message inbox for Android 10+. Designed for an arm64 Android 12 phone. **Not a SIM-call AI answering machine.**

## What works in this build
- Bundled Vosk Hindi speech recognition, no model download at first launch.
- Import audio supported by Android's codecs and save its Hindi transcript locally.
- Microphone dictation, save/delete/share transcript text.
- Separate voice-agent demo: recorded Hindi greeting, then microphone message-taking. It is a scripted flow, not an LLM chat.
- Optional Android call-screening role: exact full-number match, silence or block. Rules are OFF initially. No contacts or call-log permission is requested.
- No INTERNET permission, no backend, no paid API, Android cloud backup disabled.

## Install
Download the `hindi-voicemail-0.1.0-debug.apk` Release asset. On Android, allow installation from the app you used to download it, install, then turn that permission off again. Allow microphone access only when prompted for dictation/demo. Give the model a moment to unpack on first launch. Screening is optional and needs both a saved enabled rule and the Android screening-role prompt.

This is a **debug-signed experimental build**, not a Play Store release. It was compiled successfully but has not been verified on a Redmi Note 10. Emulator launch failed because of available memory, so actual visual/device testing is still pending. Do not rely on screening for critical calls. Future production signing would require uninstalling this debug build first; export wanted text before uninstalling.

## Limits
- Android does not expose normal SIM call audio to an ordinary installed app. Default dialer/screening status does not make this a conversational phone bot.
- No automatic carrier voicemail retrieval. Save a voicemail recording yourself and import it if your carrier supports export.
- Number format must match including country code; Android may not send contacts/withheld-number calls to this service. No country-code normalization guesses are made.
- Dictation runs only while the app is in the foreground. Raw microphone audio is not retained by dictation; transcripts are saved only on your tap.
- Imported originals are not deleted/modified. File transcription is basic mono resampling, not a high-quality audio editor. Recognition can be wrong and unsupported codecs fail with an error.
- Small Hindi model quality and speed depend on speech, noise and hardware. No accuracy guarantee.
- No local LLM included. llama.cpp/Qwen and sherpa-onnx are future options, not features secretly stubbed into this app.
- App-private storage is not separately encrypted. Anyone with access to the unlocked device can use the app. Share/export sends text through Android's chooser only when you tap it.

## Build from source
The complete Gradle project is in `source-code.zip`, including source, Gradle wrapper and greeting, but excluding build products and model weights. Extract it, install JDK 17 and Android SDK 33, set `ANDROID_HOME`, and run:

```sh
chmod +x gradlew
./prepare-model.sh
./gradlew assembleDebug
```

`prepare-model.sh` downloads the free Apache-2.0 Hindi model to build-time assets; the compiled app uses no internet. Model SHA-256 is checked. Build output: `build/outputs/apk/debug/hindi-voicemail-debug.apk` (Gradle may name it after the extracted project).

## Licenses and sources
App code: Apache-2.0. Vosk and Hindi model: Apache-2.0. JNA: dual LGPL-2.1-or-later / Apache-2.0 (choose Apache-2.0). The recorded greeting was generated with eSpeak NG; no eSpeak engine/library is linked into the APK. See THIRD_PARTY_NOTICES.md.

- https://developer.android.com/reference/kotlin/android/telecom/CallScreeningService
- https://developer.android.google.cn/media/platform/sharing-audio-input
- https://github.com/alphacep/vosk-api
- https://alphacephei.com/vosk/models
- https://github.com/k2-fsa/sherpa-onnx
- https://github.com/ggml-org/llama.cpp
- https://github.com/QwenLM/Qwen3
- https://github.com/asterisk/asterisk
