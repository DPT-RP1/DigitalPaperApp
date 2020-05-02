#!/bin/bash

# This assumes you have the android SDK with mkbootimg
# $1 folder patch containing an initramfs folder and a zImage file

cwd=$(pwd)

cd "$1/initramfs" || exit
rm -f ../initramfs.cpio

# We reset to Jan 1 1970 like Sony/Google
find . -exec touch -h --date=@0 {} + 2>/dev/null
find * 2>/dev/null | LC_COLLATE=C sort | cpio --reproducible -oav --reset-access-time --format='newc' --owner +0:+0 | gzip -c > ../initramfs.cpio.gz

# We print out the cpio content
zcat ../initramfs.cpio.gz | cpio -ivtn

cd "$cwd" || true
./mkbootimg --kernel "$1/zImage" --ramdisk "$1/initramfs.cpio.gz" -o "$1_boot.img"
rm -rf "$1/initramfs.cpio.gz"

cd "$cwd" || true