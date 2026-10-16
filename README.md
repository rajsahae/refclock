# Ref Clock

A Wear OS match clock for youth soccer refereeing: a count-up half clock with
preset checkpoints (quarter break, half end) that vibrate the watch.

- Each half's clock starts at 0:00 and keeps running past the half length (it turns amber).
- The small "Game" line is the scheduled length of earlier halves plus the current half's clock.
- Pause and End half need a long-press, so a stray touch can't stop the clock.
- While a match is on, an indicator on the watch face reopens the app, and raising your
  wrist returns to the clock rather than the watch face.

Presets live in `app/src/main/java/com/refclock/match/Presets.kt`. "Test" is there for
checking the vibrations quickly, and is listed only while Developer options are on.

## Build

Open the project in Android Studio, or from the command line:

```sh
./gradlew test             # match-clock logic
./gradlew assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
```

## Install on the watch

1. On the watch: Settings → System → About → tap Build number 7 times, then
   Settings → Developer options → enable ADB debugging and Wireless debugging.
2. Wireless debugging → Pair new device, then on the computer:

```sh
adb pair <ip>:<pairing-port>     # enter the code shown on the watch
adb connect <ip>:<port>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
