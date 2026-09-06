# ReBass

A focused Android car-audio rebass app.

## What it does
- Select a WAV audio file.
- Choose a target low frequency (20/25/30/35/40 Hz), or AUTO ÷2.
- Detects bass peaks from 25–150 Hz.
- Generates lower subharmonic content.
- Mixes it with the source and applies a soft limiter.
- Writes a new WAV file to the app cache.

## Build
Open in Android Studio, sync Gradle, then Run.

GitHub Actions builds a debug APK for **armeabi-v7a** automatically.

## Current limitation
The app decodes common Android-supported audio containers/codecs through **MediaExtractor + MediaCodec**, including MP4/M4A audio, MP3 and AAC where the device provides a decoder. Output is WAV.
