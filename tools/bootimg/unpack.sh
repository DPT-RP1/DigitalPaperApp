#!/bin/sh

# $1 img file
# $2 folder to extract to

cwd=$(pwd)

target_folder="${2:-${cwd}}"
mkdir -p "$target_folder"

./unmkbootimg "$1"

rm -rf "$target_folder/initramfs"
rm -rf "$target_folder/zImage"

mkdir -p "$target_folder/initramfs"

mv initramfs.cpio.gz "$target_folder/"
mv zImage "$target_folder/"

cd "$target_folder/initramfs" || exit
zcat ../initramfs.cpio.gz | cpio -imdv
zcat ../initramfs.cpio.gz | cpio -ivtn --numeric-uid-gid --extract-over-symlinks
rm ../initramfs.cpio.gz

cd "$cwd" || exit