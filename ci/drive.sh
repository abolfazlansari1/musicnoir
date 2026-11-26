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
T "Library"; S 03_library_songs
T "Albums"; S 04_library_albums
T "Artists"; S 05_library_artists
T "Folders"; S 06_library_folders
T "Playlists"; S 07_library_playlists
T "Songs"; sleep 1
T "Afterglow"; S 08_mini_player 3
T "Next" 0 -420 0; S 09_now_playing 3
T "Lyrics"; S 10_lyrics 5
T "Lyrics"
T "Queue"; S 11_queue 2
adb shell input keyevent 4; sleep 1
T "More" 0 0 0; S 12_track_menu 2
adb shell input keyevent 4; sleep 1
T "Sleep timer"; S 13_sleep 2
adb shell input keyevent 4; sleep 1
T "Speed and pitch"; S 14_speed 2
adb shell input keyevent 4; sleep 1
T "Close player"; sleep 2
T "Home"; S 15_home_playing 3
T "Library"; sleep 1
T "Concrete Garden" 0 0 0 long; S 16_selection 2
adb shell input keyevent 4; sleep 1
T "Search"; sleep 1
T "Songs, artists, albums"; adb shell input text "luma"; sleep 1; adb shell input keyevent 111; S 17_search 2
T "Settings"; S 18_settings
adb shell input swipe 540 1800 540 600 300; S 19_settings_2 1
adb shell input swipe 540 1800 540 600 300; S 20_settings_3 1
T "Home"; sleep 1
T "Equalizer"; S 21_equalizer 2
adb logcat -d > shots/logcat_full.txt
grep -E "FATAL|AndroidRuntime" -A8 shots/logcat_full.txt | head -80 > shots/crash.txt
ls -la shots
