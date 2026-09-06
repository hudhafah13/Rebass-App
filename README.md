# ReBass

A focused Android car-audio **rebass** app that actually works like the popular YouTube rebasses.

## What it does (proper rebass style)

1. Select any audio file (WAV, MP3, AAC, M4A…).
2. Choose a target low frequency (15–45 Hz) or **AUTO ÷2**.
3. The app:
   - Detects the original bass peaks
   - **Removes the old low end** (high-pass filter)
   - Generates new deep subharmonics that follow the energy of the original bass
   - Mixes them in cleanly and applies a soft limiter
4. Exports a clean 16-bit WAV you can share or drop into your car system.

This is the same approach used in the classic “rebass” tracks on YouTube: strip the original bass and rebuild it lower and harder for subwoofers.

## UI
- Clean dark interface
- Many frequency options (15, 18, 20, 22, 25, 27, 30, 32, 35, 38, 40, 45 Hz + AUTO)
- Amount slider
- One-tap share of the finished WAV

## Build
Open in Android Studio → Sync Gradle → Run.

GitHub Actions builds a debug APK for **armeabi-v7a** on every push to main.

## Notes
- Output is always WAV.
- Processing is offline (whole file).
- Best results with tracks that already have some low-end content for the detector to lock onto.
