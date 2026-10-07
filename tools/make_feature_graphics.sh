#!/usr/bin/env bash
# Build the 1024x500 store feature graphics from the icons and the fastlane listing text.
# Run after tools/make_store_assets.py. Needs ImageMagick (convert/identify); no SVG engine,
# which is why the icons are rendered as PNGs first and only composited here.
#
#   bash tools/make_feature_graphics.sh
#
# Fonts differ between machines, so the script resolves them at run time and fails loudly
# instead of silently producing a blank image.
set -euo pipefail
cd "$(dirname "$0")/.."

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# --- pick fonts that this machine actually has -------------------------------------------
available_fonts() { convert -list font 2>/dev/null | sed -n 's/^ *Font: //p'; }
FONTS="$(available_fonts || true)"
pick_font() { # $1..: candidates in order of preference
  local want
  for want in "$@"; do
    if printf '%s\n' "$FONTS" | grep -qx "$want"; then
      printf '%s' "$want"
      return 0
    fi
  done
  printf '%s' "$(printf '%s\n' "$FONTS" | head -1)"
}
FONT_TITLE="$(pick_font DejaVu-Sans-Bold DejaVu-Sans Liberation-Sans-Bold Nimbus-Sans-Bold)"
FONT_BODY="$(pick_font DejaVu-Sans DejaVu-Sans-Book Liberation-Sans Nimbus-Sans)"
echo "fonts: title='${FONT_TITLE:-none}' body='${FONT_BODY:-none}'"

render_text() { # $1=font $2=pointsize $3=text $4=out  -> prints width, 0 on failure
  local font="$1" size="$2" text="$3" out="$4"
  local args=(-background none -fill white)
  [ -n "$font" ] && args+=(-font "$font")
  if convert "${args[@]}" -pointsize "$size" label:"$text" -trim +repage "$out" 2>/dev/null; then
    identify -format '%w' "$out" 2>/dev/null || echo 0
  else
    echo 0
  fi
}

make_text() { # $1=font $2=pointsize $3=text $4=out $5=max-width
  local font="$1" size="$2" text="$3" out="$4" max="$5" w
  for _ in $(seq 1 14); do
    w="$(render_text "$font" "$size" "$text" "$out")"
    if [ "${w:-0}" -gt 0 ] && [ "$w" -le "$max" ]; then
      return 0
    fi
    [ "$size" -gt 18 ] || break
    size=$((size - 3))
  done
  echo "ERROR: could not render text '$text' with any size - is a font installed?" >&2
  return 1
}

make_caption() { # $1=font $2=pointsize $3=text $4=out $5=width
  local font="$1" size="$2" text="$3" out="$4" width="$5"
  local args=(-background none -fill '#AEBBCE')
  [ -n "$font" ] && args+=(-font "$font")
  convert "${args[@]}" -pointsize "$size" -size "${width}x" caption:"$text" "$out"
}

build() { # module  name  bg  accent
  local module="$1" name="$2" bg="$3" accent="$4"
  local locale icon
  icon="$module/fastlane/metadata/android/en-US/images/icon.png"
  make_text "$FONT_TITLE" 64 "$name" "$TMP/title.png" 540
  make_caption "$FONT_BODY" 29 "$(sub_of "$module")" "$TMP/sub.png" 530

  for locale in $(ls "$module/fastlane/metadata/android"); do
    convert -size 1024x500 xc:"$bg" \
      \( -size 760x760 radial-gradient:"$accent"-none -alpha set -channel A \
         -evaluate multiply 0.30 +channel \) -geometry +620-140 -composite \
      \( "$icon" -resize 300x300 \) -geometry +70+100 -composite \
      -fill "$accent" -draw "roundrectangle 434,224 502,232 4,4" \
      "$TMP/title.png" -geometry +430+140 -composite \
      "$TMP/sub.png" -geometry +434+252 -composite \
      "$module/fastlane/metadata/android/$locale/images/featureGraphic.png"
    echo "wrote $module/fastlane/metadata/android/$locale/images/featureGraphic.png"
  done
}

sub_of() { tr -d '\n' < "$1/fastlane/metadata/android/en-US/short_description.txt"; }

build cleaner   "CleanSweep"     "#070D1A" "#22D3EE"
build radio     "Ramesh Radio"   "#0B0713" "#FF8A3D"
build app       "Internet Radio" "#08111F" "#22D3EE"
build builder   "AppForge"       "#0B0F1E" "#A78BFA"
build equalizer "PulseEQ"        "#05070F" "#34D399"

echo
for f in */fastlane/metadata/android/*/images/featureGraphic.png; do
  size="$(identify -format '%wx%h' "$f")"
  if [ "$size" != "1024x500" ]; then
    echo "ERROR: $f is $size, stores want 1024x500" >&2
    exit 1
  fi
  printf '%-72s %s\n' "$f" "$size"
done
