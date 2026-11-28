#!/usr/bin/env bash
# Renders the Play Store graphics from the SVG sources in art/ (needs rsvg-convert and the fonts
# Noto Serif and Fira Sans). The launcher icon itself is the vector drawable in res/, kept in step
# with art/icon.svg by hand.
#   art/build/play-icon-512.png               Play listing icon (full square; Play rounds it)
#   art/build/feature-graphic-{de,en}.png     Play feature graphic, 1024 x 500
set -euo pipefail
cd "$(dirname "$0")/../art"
mkdir -p build
# The middle 72 x 72 of the 108 grid, as a launcher shows it.
sed 's/viewBox="0 0 108 108"/viewBox="18 18 72 72"/' icon.svg | rsvg-convert -w 512 -h 512 -o build/play-icon-512.png
# Play wants a 32-bit PNG (with alpha channel) for the icon.
magick build/play-icon-512.png PNG32:build/play-icon-512.png
for lang in de en; do
    rsvg-convert -w 1024 -h 500 "feature-graphic-$lang.svg" -o "build/feature-graphic-$lang.png"
done
ls -1 build/*.png
