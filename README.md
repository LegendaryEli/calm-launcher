# Calm Launcher

A quiet Android TV home screen for a TCL 55P715 (Android 9). A thin clock, a **Daily** row
(Netflix, Prime Video, YouTube by default), an **Other apps** row, and a slow-moving scenic
background. No ads, no recommendations.

- Move with the D-pad. The focused tile grows, gets a white ring and shows its name.
- The home screen is one **Daily** row: Netflix, Prime Video and YouTube (whichever are installed)
  with thin white marks and a faint brand glow, then an **Other apps** tile.
- Select **Other apps** to open a popup with every other app. Press Back to close it.
  The last tile in the popup is Settings.
- **Long-press OK** on an app (on the row or in the popup): Add to / Remove from Daily, Hide, App info.
- Long-press **Settings** in the popup to bring hidden apps back.
- Back does nothing on the home screen. Home jumps to the first tile.

## Screensaver

Calm Launcher also installs a screensaver: the photos full screen with a very large thin clock that
drifts slowly so nothing burns in. Turn it on under
**Settings > Device Preferences > Screen saver > Screen saver > Calm Launcher**, and set the idle time
under **When to start**. (Not yet tried on a real TV.)

## Background

Twelve Unsplash photos of Japan (credits in `CREDITS.md`) crossfade every 20 seconds with a slow
zoom. Bright photos are darkened automatically so the clock stays readable. With no photos the app
draws Mount Fuji and cycles dusk, night and dawn.

To change the set, edit the files in `app/src/main/assets/scenes/` (JPG, PNG or WebP, landscape,
about 2560 px wide is plenty). Use licensed images. The file name is the caption:
`03-Kyoto_bamboo.jpg` shows "Kyoto bamboo". A leading number sets the order and is not shown.

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
