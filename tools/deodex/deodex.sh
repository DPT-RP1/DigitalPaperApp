#!/bin/bash

# Script to deodex a Sony DPT-RP1 .apk + .odex

# $1 Folder containing /app and /framework from the diag mode (see doc/diagnosis_mode_map)
# $2 Application in the /app folder to deodex

echo "Deoptimizing boot.oat"
java -jar oat2dex.jar boot "$1/framework/arm/boot.oat"
echo "Deoptimizing $2.odex"
java -jar oat2dex.jar "$1/app/$2/arm/$2.odex" "$1/framework/arm/boot.oat-dex"
mv "$1/app/$2/arm/$2.odex-dex/$2.dex" "$1/app/$2/$2.dex"

echo "Turning dex into jar"
cd dex-tools-2.1 || exit
./d2j-dex2jar.sh -f "$1/app/$2/$2.dex" -o "$1/app/$2/$2.jar"
cd ..