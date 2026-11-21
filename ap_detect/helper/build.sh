#!/bin/bash
# Builds WifiInfo.app (universal arm64 + x86_64 when the toolchain allows it).
# Requires Xcode Command Line Tools:  xcode-select --install
# Output: ./WifiInfo.app  -> keep it next to iw.py (or inside your package).
#
# The helper targets macOS 14+, which is where Location Services started
# gating the BSSID; older systems don't need it.
#
# CODESIGN_IDENTITY defaults to ad-hoc ("-"). For distribution, use a
# "Developer ID Application: ..." identity and notarize.
set -euo pipefail
cd "$(dirname "$0")"

APP=../src/ap_detect/_bin/WifiInfo.app
rm -rf "$APP" .build
mkdir -p "$APP/Contents/MacOS" .build

built=()
for arch in arm64 x86_64; do
  if swiftc -O -target "${arch}-apple-macos14.0" WifiInfo.swift \
        -o ".build/WifiInfo-${arch}" 2> ".build/err-${arch}.log"; then
    built+=("$arch")
  else
    echo "warning: skipping ${arch} slice (details in .build/err-${arch}.log)"
  fi
done

if [ ${#built[@]} -eq 0 ]; then
  echo "error: no architecture built; see .build/err-*.log" >&2
  exit 1
fi

inputs=()
for arch in "${built[@]}"; do inputs+=(".build/WifiInfo-${arch}"); done
lipo -create "${inputs[@]}" -output "$APP/Contents/MacOS/WifiInfo"
cp Info.plist "$APP/Contents/Info.plist"

codesign --force --deep --sign "${CODESIGN_IDENTITY:--}" "$APP"
echo "Built $APP (${built[*]})"
