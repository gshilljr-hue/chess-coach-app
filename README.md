# Chess Coach — Android app

Wraps Chess Coach in a real Android app. Everything — the board, the Lozza chess
engine, the opening book, and the explanation layer — is a single HTML file inside
the APK, so the app works with no signal.

You do **not** need Android Studio, a Mac, or a fast computer. GitHub builds the APK
for you, for free, and hands you a link you can open on the phone.

> **This is a separate app from the two opening trainers.** Different package id
> (`com.chesscoach.trainer`), different icon, different repo. All three install
> side by side and none of them updates or overwrites the others.

---

## Build it (the easy way — all in a browser)

**1. Make a repository.** On [github.com](https://github.com), sign in and create a
new repository — a *new* one, not either of the trainer repos. Call it anything, e.g.
`chess-coach-app`. **Private is fine.**

**2. Upload this folder.** On the new repo's page choose **Add file → Upload files**,
then select *everything inside* this folder (not the folder itself) and drag it in.
Commit.

> **Chrome silently skips folders whose name starts with a dot**, so `.github` will
> not make it across in the drag — and `.github/workflows/build-apk.yml` is the part
> that does the building. After the first upload, check whether `.github` appears in
> the repo. If it does not: open `https://github.com/<you>/<repo>/upload/main/.github/workflows`
> (that URL puts the uploader *inside* the folder), click **choose your files**, pick
> `build-apk.yml` out of this folder's `.github\workflows`, and commit. The build
> starts by itself.

**3. Turn Actions on.** Open the **Actions** tab. If it asks, click
*I understand my workflows, go ahead and enable them*. The build starts on its own —
give it about 3–5 minutes the first time.

**4. Get the APK.** When the run finishes, go to the repo's **Releases** (right-hand
side of the main page, or `/releases`). There's a release called **latest** with
`chess-coach.apk` attached.

**5. Install it.** Open that release page *on the phone* and tap the `.apk`. Android
will say it can't install from this source — tap **Settings**, allow it for your
browser, tap back, install. That's the standard sideloading prompt; you only see it
once per browser.

Push a change later and the same `latest` release is updated in place, so the download
link never changes.

---

## Build it on a computer instead

With [Android Studio](https://developer.android.com/studio): **File → Open**, pick this
folder, let it sync, then **Build → Build Bundle(s)/APK(s) → Build APK(s)**.

From a terminal, with a JDK 17+ and the Android SDK installed:

```bash
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

---

## About the signature

The APK is signed with Android's standard **debug key**. That's deliberate: it's what
lets the cloud build run with no secrets for you to set up, and it installs and runs
exactly like any other app.

Two things to know:

- It's marked debuggable, which costs a little speed. You will not notice it here — the
  chess engine is JavaScript in a WebView either way.
- You can't put a debug-signed APK on the Play Store. For a personal app, that's moot.

**Want a properly signed release build?** Generate a keystore once:

```bash
keytool -genkey -v -keystore release.jks -keyalg RSA -keysize 2048 \
        -validity 10000 -alias trainer
```

Add `signingConfigs` to `app/build.gradle` pointing at it, store the keystore as a
base64 GitHub secret, and swap `assembleDebug` for `assembleRelease` in
`.github/workflows/build-apk.yml`. Keep the keystore file itself out of the repo —
`.gitignore` already blocks `*.jks` and `*.keystore`. Whatever you do, **don't lose
it**: without it you can't ship an update that installs over the old one.

---

## Updating the app later

The app is just a shell around one file. To ship a new version:

1. Replace `app/src/main/assets/index.html` with the new HTML.
2. Bump `versionCode` (and `versionName`) in `app/build.gradle` — Android refuses to
   install over an existing app unless `versionCode` went up.
3. Commit. The build runs and refreshes the `latest` release.

---

## What's in here

```
app/src/main/
  assets/index.html                  the whole app, engine included, offline
  java/…/MainActivity.java           ~90 lines: a WebView and a dark-mode bridge
  AndroidManifest.xml
  res/values{,-night}/               colours and themes matched to the page
  res/mipmap-*/                      launcher icons
.github/workflows/build-apk.yml      the cloud build
```

**App name:** the launcher label is `Chess Coach` (in `res/values/strings.xml`).
Android truncates long labels under the icon — shorten it there if you'd rather. If you
rename it to something containing an apostrophe, that apostrophe has to be escaped as
`\'` or `aapt2` fails the build.

**Package id:** `com.chesscoach.trainer`. Android identifies an app by this, and it is
deliberately different from `com.threeanswers.trainer` (Queen's Gambit) and
`com.moderndefence.trainer` (Modern Defence) — that is what lets all three sit on the
home screen at once. Don't change it after you've installed
the app, or the next build installs a second copy alongside the first instead of updating
it.

**Icon:** a king on slate blue — against the Queen's Gambit trainer's queen on walnut
and the Modern Defence trainer's bishop on green. The colour, not the piece, is what
makes them tellable apart at launcher size. All three are generated by
`trainer-kit/make-android-icons.js`.

**Permissions:** just `INTERNET`, and only because the page pulls three webfonts from
Google Fonts. Nothing else leaves the device — no analytics, no accounts, no data
collection. Delete that line from `AndroidManifest.xml` for a build with no network
access at all; the page falls back to system fonts and plays identically.

**Requires** Android 7.0 (API 24) or newer.

### A couple of deliberate choices

- **The back button exits the app**, the standard Android behaviour, rather than
  stepping back a move. The app has its own *Back* button, and overloading the
  system gesture would make it hard to leave. To change it anyway, override
  `onBackPressed()` in `MainActivity` and call into the page's `state.sanHistory`.
- **Dark mode is pushed in from the native side.** A WebView doesn't reliably tell a
  page about the host app's night mode, so `MainActivity` sets the page's own
  `data-theme` attribute from the system setting — which is why dark mode tracks the
  phone exactly instead of half-working.
- **System font scaling is ignored** (`setTextZoom(100)`). The board and the analysis
  panel are laid out against the page's own type scale, and a large accessibility font
  setting would otherwise push the board off-screen.

---

## A note on the engine

The analysis is done by **[Lozza](https://github.com/op12no2/lozza)** (MIT licensed), a
full-strength chess engine written in plain JavaScript with a small neural network
built in. It runs in a Web Worker inside the page, so it needs no network and no
native code.

Stockfish is not used here, and could not be: its WebAssembly build needs the
Emscripten toolchain plus a neural net of tens of megabytes, which is far past what
belongs inside an offline single-file app. Lozza is a few hundred Elo weaker than
Stockfish and far stronger than it needs to be for reviewing a club game.

**The explanations are not from the engine.** No engine writes prose — they all answer
"how good is this position in centipawns" and nothing else. The sentences come from a
chess-knowledge layer written for this app (`Annotator` in the page source), which
looks at what a move develops, blocks, unblocks, attacks, defends, weakens or prepares.
The engine's job is only to say whether the move worked.

The **engine speed** selector trades accuracy for time. On *quick* a deep sacrifice can
be misjudged for a few moves; on *careful* the engine finds things like a queen
sacrifice mating in two. If a verdict looks wrong on a sharp move, re-run at *careful*.
