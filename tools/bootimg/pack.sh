#!/bin/bash

# This assumes you have the android SDK with mkbootimg
# $1 folder patch containing an initramfs folder and a zImage file

cwd=$(pwd)

cd "$1/initramfs" || exit

find . -depth -print 2>/dev/null | cpio -ov > ../initramfs.cpio
cd ..
gzip initramfs.cpio

cd "$cwd" || true
./mkbootimg --kernel "$1/zImage" --ramdisk "$1/initramfs.cpio.gz" -o "$1_boot.img"

cd "$cwd" || true