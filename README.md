# Gallery Date Fixer

An Android app that restores the correct chronological order of photos and videos in
Samsung Gallery / Google Photos, in the spirit of the Play Store app *Image & Video Date Fixer*.

Photos that were copied, restored from a backup, downloaded or received via WhatsApp often get
"today" as their modified date, so the Gallery shows them in the wrong place. This app scans a
folder, works out when each photo/video was really taken, and sets the file's
**last-modified date** to that date (and optionally writes the **EXIF date taken**), then
refreshes Android's media index so the Gallery re-sorts them.

## How the date is determined

For every image/video in the folder (optionally including subfolders):

1. **Embedded metadata** – EXIF `DateTimeOriginal` (JPEG, HEIC, PNG, WebP, DNG…) or the video's
   creation date (MP4, MOV, 3GP…).
2. **File name** – e.g.
   `20230514_181530.jpg` (Samsung), `IMG_20230514_181530.jpg`, `VID_20230514_181530.mp4`,
   `PXL_20230514_181530123.jpg`, `Screenshot_20230514-181530_Chrome.jpg`,
   `Screenshot_2023-05-14-18-15-30.png`, `PHOTO-2023-05-14-18-15-30.jpg`,
   `signal-2023-05-14-181530.jpg`, `IMG-20230514-WA0007.jpg` (WhatsApp),
   `FB_IMG_1684080930000.jpg` (Unix timestamps).
   WhatsApp names only contain the day, so the time is kept if the file's current date is on that
   day; otherwise it becomes 12:00 plus the WA sequence number in seconds, preserving their order.

You can switch the priority with **Prefer date from file name**.

You get a **preview** first (current date → new date, and where it came from). Nothing is
changed until you tap **Fix**.

## What "Fix" does

1. *(optional, default on)* writes EXIF `DateTimeOriginal`/`DateTimeDigitized` + time-zone offset
   into JPEG/PNG/WebP files that have no EXIF date (or a different one when "prefer file name" is on);
2. sets the file's last-modified time to the detected date;
3. runs Android's media scanner on the changed files so MediaStore (and the Gallery) pick it up.

## Build & install (Android Studio)

1. Install the latest **Android Studio** and open this folder (`File › Open…`).
   Let Gradle sync; accept any prompt to install the Android SDK platform 36.
2. On your Galaxy S24 Ultra enable **Developer options** (Settings › About phone › Software
   information › tap *Build number* 7×) and turn on **USB debugging** (or Wireless debugging).
3. Connect the phone, select it in the device dropdown and press **Run ▶**.
4. On first start tap **Grant all files access** and enable *Allow access to manage all files*.
   This permission is required: Android only lets an app change the dates of files created by other
   apps (Camera, WhatsApp…) when it has it.

To build an APK instead: `Build › Build App Bundle(s) / APK(s) › Build APK(s)`
(or `./gradlew assembleDebug`) – output in `app/build/outputs/apk/`.

Requirements: Android 11 (API 30) or newer; targets Android 16 (API 36).

## Tips

- Try a small folder first, and keep a backup of irreplaceable photos (writing EXIF rewrites the file).
- If Samsung Gallery still shows the old order, close it from Recents and reopen it; it can take a
  minute to sync with the media index.
- Files in the "No date" list have no usable date in either metadata or name and are left untouched.

## Project layout

```
app/src/main/java/com/alibian/gallerydatefixer/
  core/FilenameDateParser.kt  – date patterns in file names (pure Kotlin, unit tested)
  core/MetadataDates.kt       – EXIF / video date parsing & formatting (unit tested)
  core/MediaScanner.kt        – walks a folder, decides each file's target date
  core/DateFixer.kt           – writes EXIF, sets modified date, refreshes MediaStore
  core/Storage.kt             – storage roots, folder shortcuts, permission check
  MainViewModel.kt            – UI state, scan / fix jobs
  ui/Screens.kt               – Jetpack Compose UI (folder browser, preview, results)
```

Run the unit tests with `./gradlew test`.
