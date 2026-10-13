# libs/

Vendored binary artifacts resolved by the Gradle build through the `flatDir` repository declared in
`settings.gradle.kts`.

These files are **binaries and are not committed**. Fetch them with:

```bash
./scripts/fetch-sherpa-aar.sh
```

| Artifact              | Version | License    | Consumed by    |
| --------------------- | ------- | ---------- | -------------- |
| `sherpa-onnx-<v>.aar` | 1.13.8  | Apache-2.0 | `:asr`, `:tts` |

The download is verified against a pinned SHA-256 in the fetch script.
