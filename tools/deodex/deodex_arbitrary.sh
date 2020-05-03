#!/bin/bash

# Script to deodex a Sony DPT-RP1 .apk + .odex

# $1 Folder containing /app and /framework from the diag mode (see doc/diagnosis_mode_map)
# $2 odex file location without extension
# $3 File name without extension

echo "Deoptimizing boot.oat"
java -jar oat2dex.jar boot "$1/framework/arm/boot.oat"
echo "Deoptimizing $2.odex"
java -jar oat2dex.jar "$2.odex" "$1/framework/arm/boot.oat-dex"

echo "Turning dex into jar"
cd dex-tools-2.1 || exit
./d2j-dex2jar.sh -f "$2.odex-dex/$3.dex" -o "$2.jar"
cd ..