#!/usr/bin/env bash
# Монтаж трейлера: склеить части (кадр в кадр, 60 в секунду), дать цвет и зерно, наложить музыку.
# Вспышки, затемнения и кашетирование уже вшиты в кадры игрой — здесь только общий тон.
#   edit.sh <папка с partN.mkv> <music.wav> <выход.mp4>
set -euo pipefail
parts="$1"
music="$2"
out="$3"
work="$(mktemp -d)"

: > "$work/list.txt"
for i in 1 2 3 4; do
  echo "file '$(realpath "$parts/part$i.mkv")'" >> "$work/list.txt"
done
ffmpeg -hide_banner -loglevel warning -y -f concat -safe 0 -i "$work/list.txt" -c copy "$work/joined.mkv"
ffprobe -v error -count_frames -select_streams v:0 -show_entries stream=nb_read_frames,r_frame_rate -of csv=p=0 "$work/joined.mkv"

# Холодные тени, тёплые света, приглушённый цвет, контраст, виньетка и лёгкое неподвижное зерно (зерно,
# меняющееся каждый кадр, раздувает файл в разы); полосы кашетирования после зерна снова чисто чёрные.
grade="eq=contrast=1.12:saturation=0.84:gamma=0.96"
grade="$grade,colorbalance=rs=-0.03:gs=-0.005:bs=0.04:rh=0.035:gh=0.01:bh=-0.025"
grade="$grade,curves=all='0/0 0.12/0.08 0.5/0.5 0.88/0.92 1/1'"
grade="$grade,vignette=angle=PI/4.6"
grade="$grade,noise=alls=3:allf=u"
grade="$grade,drawbox=x=0:y=0:w=iw:h=138:color=black:t=fill,drawbox=x=0:y=ih-138:w=iw:h=138:color=black:t=fill"
grade="$grade,format=yuv420p"

ffmpeg -hide_banner -loglevel warning -y -i "$work/joined.mkv" -i "$music" \
  -filter_complex "[0:v]$grade[v]" -map "[v]" -map 1:a \
  -c:v libx264 -preset slow -crf 18 -tune film -profile:v high -r 60 \
  -c:a aac -b:a 320k -shortest -movflags +faststart "$out"
rm -rf "$work"
