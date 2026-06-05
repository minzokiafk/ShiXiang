# Third-Party Notices

This project includes or depends on third-party open source software. Each component remains under its own license.

## Runtime and libraries

- Android Gradle Plugin
- Kotlin and Kotlin Coroutines
- AndroidX, Jetpack Compose, Material 3, Media3, DocumentFile, Core SplashScreen
- Chaquopy
- yt-dlp
- yt-dlp-ejs
- Coil
- Haze

## FFmpeg

The Android build includes a prebuilt FFmpeg executable library under:

```text
app/src/main/jniLibs/arm64-v8a/libffmpeg_exec.so
```

The bundled FFmpeg notice files are kept under:

```text
app/src/main/assets/licenses/
```

Review `ffmpeg-prebuilt-README.md` and `ffmpeg-prebuilt-LICENSE.md` before redistributing binaries.

## Supported-sites data

`app/src/main/assets/supportedsites.md` is used to describe supported extractor coverage and should be kept in sync with the downloader engine it documents.

## Trademarks

Platform names and logos, including YouTube, Bilibili, TikTok, Douyin, Xiaohongshu, Weibo, Instagram, Threads, X/Twitter and other services, are trademarks of their respective owners. This project is not affiliated with or endorsed by those platforms.

## User responsibility

This software is intended for downloading content the user is authorized to access and store. The project does not grant rights to download copyrighted, paid, private, DRM-protected, or otherwise restricted content.
