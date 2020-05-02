#!/bin/bash

# $1 folder path of the official firmware extract, that contains an initramfs folder itself
# $2 patch path
# $3 resulting folder

# e.g.: ./patch.sh ./official adb.patch adb

patch_file=$2
patch_name=$3
# Patches the official initram fs, assuming the patch was create with "official" as the official folder extract
rm -rf "$1_backup"
cp -R "$1" "$1_backup"

mv "$1" ./official 2>/dev/null || true
echo "Patching ..."
patch -s -p0 < "$patch_file"
mv ./official "$patch_name" 2>/dev/null || true
mv "$1_backup" "$1" 2>/dev/null || true
echo "Patched to $patch_name"