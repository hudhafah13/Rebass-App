# ReBass v1.2

A focused Android car-audio **rebass** app that works like popular YouTube rebasses.

## What it does
1. Select any audio file (WAV, MP3, AAC, M4A…).
2. Choose a target low frequency (**15–75 Hz**) or **AUTO ÷2**.
3. The app:
   - Detects original bass peaks
   - **Removes the old low end** (high-pass)
   - Generates new deep subharmonics that follow the energy of the original bass
   - Shows a **before / after bass viewport**
   - Mixes them in + soft limiter
4. Exports a clean 16-bit WAV you can share.

Default rebass amount is **100%**.

## Crash resistance
- Processes long files in 8-second chunks
- Caps bass analysis to ~45 seconds of audio
- `largeHeap` enabled
- Catches OutOfMemoryError with a friendly message

## Build
Open in Android Studio → Sync → Run, or use the GitHub Action **Build ReBass APK**.
