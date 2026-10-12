# Held-out ASR fixture set

Tiny, committed, **model-free** inputs for the SenseVoice ASR engine. They exist so the fixture
plumbing can be validated on the host (`AsrFixtureSetTest`) and so a future **Tier B device run**
has concrete inputs with pinned expectations.

No real ASR model is bundled or fetched in this repository, so the clips are deliberately
**non-speech** (silence / tone / noise) with an expected **empty** transcript. Positive (speech)
clips plus their expected text are added at device-run time from the model's own `test_wavs` and the
Wave-0 S4 verified transcripts (e.g. `yue.wav` -> `呢几个字都表达唔到我想讲嘅意思`).

## Format

| file                           | format            | expected transcript |
| ------------------------------ | ----------------- | ------------------- |
| `silence_0.5s_16k_mono.wav`    | 16 kHz mono int16 | ""                  |
| `tone_440hz_0.5s_16k_mono.wav` | 16 kHz mono int16 | ""                  |
| `noise_0.5s_16k_mono.wav`      | 16 kHz mono int16 | ""                  |

`transcripts.tsv` is the machine-readable manifest (`fixture <TAB> expected <TAB> note`).

## Device run (Tier B)

On an arm64 device with the SenseVoice model installed, transcribe each clip through
`SenseVoiceAsrEngine` and compare the output to the manifest. Word error rate (WER) is reported for
the held-out set; the ASR segment-latency budget to hold is <= 1500 ms per segment (see the plan's
Budgets). On the host this run auto-skips: `SKIPPED: no device`.
