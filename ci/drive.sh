#!/bin/bash
# Runs inside the emulator: installs the app, seeds media, walks the main screens and saves screenshots + logcat.
mkdir -p shots
S() { sleep ${2:-2}; adb exec-out screencap -p > shots/$1.png; }
T() { python3 ci/tap.py "$@"; }
adb wait-for-device
adb shell settings put global window_animation_scale 0; adb shell settings put global transition_animation_scale 0; adb shell settings put global animator_duration_scale 0
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell mkdir -p /sdcard/Music/Noir
for f in ci/media/*.mp3; do adb push "$f" /sdcard/Music/Noir/ >/dev/null; done
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Music/Noir >/dev/null
adb shell content call --uri content://media --method scan_volume --arg external_primary >/dev/null 2>&1
sleep 8
echo "audio rows in MediaStore:"; adb shell content query --uri content://media/external/audio/media --projection title | wc -l
adb shell pm grant com.noirmusic.app android.permission.READ_MEDIA_AUDIO
adb shell pm grant com.noirmusic.app android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -n com.noirmusic.app/.MainActivity
S 01_home 6
adb shell input swipe 540 1800 540 700 300; S 02_home_scrolled 1
adb shell input swipe 540 700 540 1800 300
T "Library"; S 03_library_songs
T "Albums"; S 04_library_albums
T "Artists"; S 05_library_artists
T "Folders"; S 06_library_folders
T "Playlists"; S 07_library_playlists
T "Songs"; sleep 1
T "Midnight Drive"; S 08_mini_player 3
adb shell input tap 400 2150; S 09_now_playing 3
T "Lyrics"; S 10_lyrics 4
T "Lyrics"
T "Queue"; S 11_queue 2
adb shell input keyevent 4; sleep 1
T "Equalizer"; S 12_equalizer 2
adb shell input keyevent 4; sleep 1
adb shell input keyevent 4; sleep 1
T "Search"; sleep 1; adb shell input tap 540 520; adb shell input text "luma"; S 13_search 2
adb shell input keyevent 4
T "Settings"; S 14_settings
adb shell input swipe 540 1800 540 600 300; S 15_settings_2 1
adb shell input swipe 540 1800 540 600 300; S 16_settings_3 1
adb logcat -d > shots/logcat_full.txt
grep -E "FATAL|AndroidRuntime|com.noirmusic" shots/logcat_full.txt | head -200 > shots/logcat_app.txt
adb shell dumpsys activity services com.noirmusic.app | head -30 > shots/services.txt
ls -la shots
