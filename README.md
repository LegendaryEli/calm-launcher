# Calm Launcher

A quiet Android TV home screen for a TCL 55P715 (Android 9). A thin clock, a **Daily** row
(Netflix, Prime Video, YouTube by default), an **Other apps** row, and a slow-moving scenic
background. No ads, no recommendations.

- Move with the D-pad. The focused tile grows, gets a white ring and shows its name.
- **Long-press OK** on an app: Add to / Remove from Daily, Hide, App info.
- **All apps** lists everything. Long-press **Settings** to bring hidden apps back.
- Back does nothing. Home jumps to the first tile.
- The three streaming apps get thin white line icons. Any other app uses its own icon.

## Background

With no photos the app draws Mount Fuji and cycles dusk, night and dawn. To use photos instead,
put JPG, PNG or WebP files in `app/src/main/assets/scenes/`. They crossfade every 20 seconds
with a slow zoom. Use licensed images (Unsplash, Pexels, or your own), landscape, at least
1920 px wide, with calm sky at the top left and nothing bright along the bottom third.
The file name is the caption: `03-Kyoto_bamboo.jpg` shows "Kyoto bamboo" (a leading number sets the
order and is not shown).

## Build without a computer (from an iPad)

1. Create a GitHub repo and put this folder's contents in it (GitHub web upload, or Working Copy).
   The workflow lives in `.github/workflows/build.yml`.
2. Open the repo's **Actions** tab. "Build APK" runs on every push and takes about 5 minutes.
3. It publishes a release containing `app-debug.apk`.

## Install on the TV

1. Install **Downloader** from the Play Store on the TV.
2. In Downloader enter `https://github.com/YOUR_NAME/YOUR_REPO/releases/latest/download/app-debug.apk`
   (the repo has to be public for this link to work). Allow "Install unknown apps" for Downloader.
3. Open Calm Launcher from the apps list, or press Home and choose it if the TV asks.

## Make Home open it (needs a computer once, or Wi-Fi ADB)

```
adb connect TV_IP:5555
adb shell cmd package set-home-activity com.calmlauncher/.MainActivity
```

If the stock launcher still wins, it can be disabled and re-enabled:

```
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell pm enable com.google.android.tvlauncher
```

## Build locally

Open the folder in Android Studio and sync (Gradle 8.7, JDK 17), then Build > Build APK(s).
