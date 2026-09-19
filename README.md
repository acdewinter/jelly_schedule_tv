# Jelly Schedule TV

A native Android TV companion app for the [Jelly Schedule](https://github.com/acdewinter/jelly_schedule_plugin)
Jellyfin plugin. Open the app and, if something is on, it is already playing. Between programmes there is a
countdown to the next one; when the evening's viewing window is over the channel goes off air.

This is deliberately not a library browser: the official Jellyfin app remains for that, and the schedule is
edited in the plugin's web app.

## Building

```
./gradlew :app:assembleDebug
```

Requires JDK 17+, the Android SDK with platform 36, and network access to Maven Central and Google's Maven
repository. Android Studio opens the project as-is. A GitHub Actions workflow builds a debug APK on every push.

## Development against the mock server

The plugin repository ships `tools/DevHost`, a .NET program that runs the real plugin API against an in-memory
fake library and mocks the Jellyfin endpoints the app needs:

```
cd jelly_schedule_plugin/tools/DevHost
JS_SAMPLE_VIDEO=/path/to/sample.mp4 dotnet run
```

Point the app at `http://10.0.2.2:5000` from the emulator (or the machine's LAN address from a TV) and sign in
as `household` with any password.

## License

MIT, see `LICENSE`.
