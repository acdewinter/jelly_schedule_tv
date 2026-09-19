# Jelly Schedule TV

A native Android TV companion app for the [Jelly Schedule](https://github.com/acdewinter/jelly_schedule_plugin)
Jellyfin plugin. Jelly Schedule turns a Jellyfin library into a weekly TV channel; this app brings that channel
to the living-room TV with a native player.

The feeling it aims for is **turning on the TV**: open the app and, if something is on, it is already playing.
Between programmes there is a countdown to the next one. When the evening's viewing window is over, the channel
goes off air and the app says so, on purpose. It is deliberately not a library browser (the official Jellyfin app
remains for that) and the schedule itself is edited in the plugin's web app.

## What it does

- **Connect and sign in** to any Jellyfin server with the plugin installed: type the address or pick a server
  found on the local network; sign in with a password or with Quick Connect. The server, token and a stable
  device id are remembered on the TV. Any user may sign in; a banner tells you when that is not the household
  user whose watch history drives the schedule.
- **Tune in**: the launch screen asks the plugin what is on. If something is on it starts playing (the plugin's
  `AutoplayOnOpen` and a client-side toggle both apply; otherwise there is a big *Tune in* button). If the channel
  is off air you see what is up next with a countdown, or when the channel is back, plus Guide, Recordings and
  Watch early.
- **Player**: Media3 ExoPlayer, full screen, with a 10-foot overlay (LIVE / RECORDING bug, title, progress,
  "Next: … at 20:30"). D-pad: centre = play/pause, left/right = seek ±10 s (hold for ±60 s), down = controls,
  back = close. Subtitle and audio menus. Direct play when the TV can decode the file (the device profile is
  built from the TV's real decoders), otherwise an HLS transcode; a decoder failure falls back to transcoding
  automatically. When a programme ends the player follows the channel: the next programme, a countdown
  interstitial, or off air.
- **Guide**: this week / next week, Mon–Sun day tabs, a D-pad friendly list of the day's programmes with time,
  poster, title and badges (On now, Movie, Re-run, Premiere, Finale, Rec, Watched, Missed), away days and
  off-air days. Selecting a programme opens its details with Watch / Tune in / Resume, Record for later,
  Cancel recording and Mark watched / unwatched.
- **Recordings**: programmes deferred to watch later, with Play / Resume and Cancel; watched ones in their own
  section.
- **Settings**: server and account, sign out, autoplay on launch, preferred subtitle language, an
  always-transcode switch, what this TV can decode, and a read-only summary of the household schedule.
- **Google TV home**: the programme that is on is published to the Watch Next row while it plays.

Playback is reported to Jellyfin every 10 s and on pause / seek, so the household's watch history advances.
If the signed-in user is not the household user, the position and completion are mirrored to the plugin's
`playstate` endpoint as well.

## Installing on a TV (sideloading)

1. Download `app-debug.apk` from the latest **Build** workflow run, or a signed `jelly-schedule-tv-x.y.z.apk`
   from the [releases](../../releases).
2. On the TV enable *Developer options → USB debugging* (Settings → Device Preferences → About → tap *Build*
   seven times, then Developer options → USB debugging / Network debugging).
3. From a computer on the same network:

   ```
   adb connect <tv-ip>:5555
   adb install -r jelly-schedule-tv-x.y.z.apk
   ```

   Alternatively copy the APK to a USB stick and install it with a file manager that can open APKs, or use an
   app such as *Send files to TV* or *Downloader*.
4. Start **Jelly Schedule** from the TV launcher, enter the address of your Jellyfin server (the same one you use
   in a browser, for example `http://192.168.1.10:8096`) and sign in.

The app needs Android 8.0 (API 26) or newer, a leanback (TV) device, and the Jelly Schedule plugin on the server.

## Building

```
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest    # unit tests
```

Requirements: JDK 17 or newer, the Android SDK with `platforms;android-36` and `build-tools;35.0.0`, and network
access to Maven Central and Google's Maven repository. Android Studio opens the project as-is. Put the SDK
location in `local.properties` (`sdk.dir=/path/to/sdk`) or set `ANDROID_HOME`.

Run it on an Android TV emulator image (API 34, 1080p) from Android Studio, or on a real device with
`adb connect <tv-ip>:5555 && adb install -r app/build/outputs/apk/debug/app-debug.apk`. Test focus handling
with the keyboard arrows in the emulator: everything must be reachable with a D-pad only.

## Development against the mock server

The plugin repository ships `tools/DevHost`, a .NET 10 program that runs the real plugin API against an
in-memory fake library and mocks the Jellyfin endpoints the app needs (authentication, `PlaybackInfo`, a sample
video stream, subtitles, play-state reporting, images):

```
cd jelly_schedule_plugin/tools/DevHost
JS_SAMPLE_VIDEO=/path/to/sample.mp4 dotnet run
```

Point the app at `http://10.0.2.2:5000` from the emulator (or the machine's LAN address from a TV) and sign in as
`household` with any password (`kid` is a non-household user, handy for testing the mirror banner). The DevHost's
`/dev/reset` and `/dev/watched/{id}` helpers seed state; `tools/ui-test/ui-test.mjs` in the plugin repository
shows a full seeding sequence (windows, lineup, movie night, a watched episode, a recording). The JSON fixtures
under `app/src/test/resources/fixtures` were captured from a DevHost seeded that way.

## Releases

Pushing a tag `vX.Y.Z` runs the **Release** workflow, which builds a signed APK (`versionName` = the tag,
`versionCode` = the workflow run number) and attaches it to a GitHub release. It needs four repository secrets:

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | the keystore file, base64 encoded (`base64 -w0 release.jks`) |
| `SIGNING_KEYSTORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | key alias |
| `SIGNING_KEY_PASSWORD` | key password |

Create a keystore once with `keytool -genkeypair -v -keystore release.jks -alias jellyschedule -keyalg RSA -keysize 4096 -validity 10000`.
Locally, `./gradlew :app:assembleRelease` signs with the debug key unless the same variables are set in the
environment (`SIGNING_KEYSTORE_FILE` pointing at the `.jks`).

## Project layout

```
app/src/main/kotlin/dev/jellyschedule/tv/
  data/json        kotlinx.serialization setup (PascalCase, lenient, omitted nulls)
  data/model       every object of the plugin API
  data/api         OkHttp client for /JellySchedule/
  data/session     DataStore: server, token, device id, client preferences
  data/repo        SessionRepository (Jellyfin SDK: auth, discovery, images),
                   ScheduleRepository (plugin), PlaybackRepository (PlaybackInfo, reporting)
  domain           pure rules: tune-in position, auto-advance, guide weeks, presentation
  player           device profile from MediaCodecList
  tvprovider       Watch Next publishing
  ui               Compose for TV screens: connect, sign-in, tune-in, player, guide,
                   details, recordings, settings; one NavHost, a ViewModel per screen
app/src/test       unit tests (JSON fixtures from the DevHost, tune-in and auto-advance rules, guide grouping)
```

Out of scope for v1, by design: editing viewing windows or the lineup, movie night settings, one-offs, blackouts,
Sonarr/Radarr configuration and casting. The web app remains the place for those.

## License

MIT, see `LICENSE`.
