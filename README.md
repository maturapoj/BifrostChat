# BifrostChat

[![CI](https://img.shields.io/github/actions/workflow/status/maturapoj/BifrostChat/ci.yml?branch=main&label=CI&logo=github)](https://github.com/maturapoj/BifrostChat/actions/workflows/ci.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![Material 3](https://img.shields.io/badge/Design-Material%203-757575?logo=materialdesign&logoColor=white)](https://m3.material.io)
[![Android Min SDK](https://img.shields.io/badge/Android%20Min%20SDK-26-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-36-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/16)
[![Async](https://img.shields.io/badge/Async-Coroutines%20%2B%20Flow-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/flow.html)
[![HTTP](https://img.shields.io/badge/HTTP-OkHttp%204.12-3E8E41?logo=square&logoColor=white)](https://square.github.io/okhttp/)
[![Streaming](https://img.shields.io/badge/Streaming-SSE-FF6F00)](https://developer.mozilla.org/docs/Web/API/Server-sent_events)
[![API](https://img.shields.io/badge/API-OpenAI%20compatible-412991)](https://platform.openai.com/docs/api-reference/chat/streaming)
[![License](https://img.shields.io/badge/License-MIT-C9A227)](LICENSE)

A Jetpack Compose chat client for experimenting with **LLM token streaming** on Android.
It talks to any OpenAI-compatible gateway (`/v1/chat/completions` with `stream: true`)
and renders the reply as it arrives: reasoning tokens and answer tokens appear separately.

<img src="docs/screenshot.png" width="300" alt="Chat screen showing a collapsible reasoning block, a streamed answer, and a stats line with TTFT, token count and tok/s">

## Features

- Streams tokens over server-sent events. **Stop** cancels the stream and closes the HTTP connection.
- Shows reasoning tokens (`delta.reasoning` / `delta.reasoning_content`) in a collapsible "Thinking…" block.
- Batches UI updates (at most one every 50 ms) instead of recomposing once per chunk.
- Shows stats under each reply: time to first token, number of UI updates, completion and reasoning tokens, total time, tok/s.
- Loads the model picker from `/v1/models`.

## How the stream flows

```text
OkHttp response body
  └─ readUtf8Line()              one SSE line at a time, as soon as it arrives
      └─ SseChunkParser          "data: {...}" → Reasoning / Content / Usage / Finished
          └─ Flow<StreamEvent>   BifrostClient.streamChat(), runs on Dispatchers.IO
              └─ coalesceTokens()  batches deltas on a 50 ms timer
                  └─ ChatViewModel  appends deltas to the last message in a StateFlow
                      └─ ChatScreen  LazyColumn(reverseLayout = true) keeps the newest text in view
```

| File | Role |
| --- | --- |
| [`data/BifrostClient.kt`](app/src/main/java/com/example/bifrostchat/data/BifrostClient.kt) | HTTP calls; turns the SSE body into a cancellable `Flow` |
| [`data/SseChunkParser.kt`](app/src/main/java/com/example/bifrostchat/data/SseChunkParser.kt) | Parses one SSE line; `[DONE]` ends the stream, `error` payloads throw |
| [`data/CoalesceTokens.kt`](app/src/main/java/com/example/bifrostchat/data/CoalesceTokens.kt) | Timer-based batching of token deltas |
| [`ui/ChatViewModel.kt`](app/src/main/java/com/example/bifrostchat/ui/ChatViewModel.kt) | Chat state, send/stop, stream stats |
| [`ui/ChatScreen.kt`](app/src/main/java/com/example/bifrostchat/ui/ChatScreen.kt) | Compose UI |

## Notes from testing

**The gateway delivers chunks in bursts.** Chunk arrival times measured with curl looked like this:
the first ~20 chunks arrived together at 2.5 s, then more came in groups about a second apart.
This affected two design choices:

- **Throttling has to flush on a timer.** A throttle that only emits when the next token arrives
  holds the tail of each burst until the next burst, about a second later.
  `coalesceTokens()` starts a timer at the first buffered token and flushes when it fires.
  `Usage` and `Finished` flush the buffer first, then pass through.
- **tok/s is measured over the whole request.** Measuring from first to last token gave
  values like 2,000+ tok/s, because a whole burst lands in a few milliseconds.

**Auto-scroll uses `reverseLayout`.** Scrolling to the last item after each update missed
the bottom when the message grew or the keyboard opened. A reversed list keeps index 0
(the newest message) anchored to the bottom.

## Setup

Requirements: Android SDK (compileSdk 36), JDK 17+.

```sh
cp local.properties.example local.properties
```

Fill in `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
bifrost.baseUrl=https://your-gateway.example.com   # without /v1
bifrost.apiKey=sk-...
```

Then:

```sh
./gradlew installDebug        # build and install on a device or emulator
./gradlew testDebugUnitTest   # parser and coalescing tests (virtual time, no network)
```

> [!WARNING]
> The API key is compiled into `BuildConfig`, so anyone who has the APK can extract it.
> This is fine for local experiments. Do not distribute builds made with a real key;
> a production app should call its own backend instead.

## Stack

Kotlin 2.2 · Jetpack Compose (BOM 2026.02) · Material 3 · Coroutines/Flow · OkHttp 4.12 · `org.json`

## License

[MIT](LICENSE)
