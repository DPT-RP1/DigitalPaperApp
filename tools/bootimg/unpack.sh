#!/bin/sh

# $1 img file
# $2 folder to extract to

cwd=$(pwd)
echo "Current directory: $cwd"

target_folder="${2:-${cwd}}"
echo "Destination: $target_folder"
mkdir -p "$target_folder"

./unmkbootimg "$1"

rm -rf "$target_folder/initramfs"
rm -rf "$target_folder/zImage"

mkdir -p "$target_folder/initramfs"
mkdir -p "$target_folder/zimage"

mv initramfs.cpio.gz "$target_folder/"
mv zImage "$target_folder/"

cd "$target_folder/initramfs" || exit
zcat ../initramfs.cpio.gz | cpio -imdv

cd "$cwd" || exit