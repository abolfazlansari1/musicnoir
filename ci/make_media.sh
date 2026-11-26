#!/bin/bash
# Generates small tagged MP3 files with cover art so the app has realistic content on the emulator.
set -e
out=ci/media; mkdir -p $out
make_cover() { # name hue-start hue-end
  ffmpeg -loglevel error -y -f lavfi -i "gradients=s=600x600:c0=$2:c1=$3:x0=0:y0=0:x1=600:y1=600:d=1:r=1" -frames:v 1 $out/$1.png 2>/dev/null \
  || ffmpeg -loglevel error -y -f lavfi -i "color=c=$2:s=600x600:d=1" -frames:v 1 $out/$1.png
}
make_cover c1 0x7a3cff 0xff5c8a
make_cover c2 0x1fd1a5 0x1a3cff
make_cover c3 0xffb454 0xd1305a
make_cover c4 0x2a2a40 0x8a6cff
n=0
track() { # file title artist album trackno cover freq dur
  n=$((n+1))
  ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=$7:duration=$8" -i $out/$6.png -map 0 -map 1 \
    -c:a libmp3lame -b:a 48k -ac 1 -c:v mjpeg -id3v2_version 3 -disposition:v attached_pic \
    -metadata title="$2" -metadata artist="$3" -metadata album="$4" -metadata track="$5" -metadata date=2024 \
    $out/$1.mp3
}
track 01 "Midnight Drive" "Aurora Vale" "Neon Skyline" 1 c1 220 214
track 02 "Glass Rain" "Aurora Vale" "Neon Skyline" 2 c1 262 187
track 03 "Afterglow" "Aurora Vale" "Neon Skyline" 3 c1 294 243
track 04 "Paper Moons" "Aurora Vale" "Neon Skyline" 4 c1 330 201
track 05 "Harbor Lights" "Neon Harbor" "Low Tide" 1 c2 247 232
track 06 "Salt & Static" "Neon Harbor" "Low Tide" 2 c2 277 198
track 07 "Driftwood" "Neon Harbor" "Low Tide" 3 c2 311 256
track 08 "Slow Burn" "The Midnight Owls" "After Hours" 1 c3 196 221
track 09 "Velvet Room" "The Midnight Owls" "After Hours" 2 c3 233 176
track 10 "Last Train Home" "The Midnight Owls" "After Hours" 3 c3 262 289
track 11 "Golden Hour" "Kian Rahimi" "Single" 1 c4 349 203
track 12 "Echoes" "Kian Rahimi" "Single" 2 c4 392 195
track 13 "Satellite" "Orbit & Co" "Far Side" 1 c2 415 228
track 14 "Moonwalk" "Orbit & Co" "Far Side" 2 c2 440 211
track 15 "Daydream" "Luma" "Soft Focus" 1 c1 330 184
track 16 "Open Roads" "Luma" "Soft Focus" 2 c1 370 238
track 17 "شب های تهران" "سارا" "خاطره" 1 c3 247 205
track 18 "بارون" "سارا" "خاطره" 2 c3 277 193
track 19 "Concrete Garden" "Vesper" "Bloom" 1 c4 207 217
track 20 "Wildfire" "Vesper" "Bloom" 2 c4 233 242
ls -la $out | head -40
