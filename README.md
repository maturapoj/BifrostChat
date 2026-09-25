# BifrostChat

Jetpack Compose chat client that streams tokens from an OpenAI-compatible gateway
(`/v1/chat/completions` with `stream: true`), built to experiment with token streaming.

- Server-sent events are read line by line with OkHttp and exposed as `Flow<StreamEvent>`
  (`Reasoning`, `Content`, `Usage`, `Finished`).
- `coalesceTokens()` batches deltas on a timer so the UI updates at most every 50 ms,
  without holding the tail of a burst until the next one arrives.
- Reasoning tokens show in a collapsible block; each reply shows TTFT, update count,
  token usage and tok/s.
- The model list comes from `/v1/models`. Stop cancels the stream and closes the connection.

## Setup

```sh
cp local.properties.example local.properties   # then fill in sdk.dir, base URL and API key
./gradlew installDebug                          # JDK 17+
./gradlew testDebugUnitTest
```

The API key is compiled into `BuildConfig`, so do not distribute builds made with a real key.
