#!/bin/bash

# This is a convenience script to remeber the diff options
# $1 folder1
# $2 folder2

diff -qr "$1/initramfs/" "$2/initramfs/"
diff -ruN "$1/initramfs/" "$2/initramfs/" > initramfs.patch # Will not persist binary
cwd=$(pwd)
# Compare file permission
cd "$1/initramfs" || exit
find . -exec stat --format='%n %A %U %G' {} \; | sort > "$cwd/$1.list"

cd "$cwd" || exit
cd "$2/initramfs" || exit
find . -exec stat --format='%n %A %U %G' {} \; | sort > "$cwd/$2.list"

cd "$cwd" || exit
diff "$1.list" "$2.list"
rm "$1.list" "$2.list"

# sudo apt install meld for a GUI
# diff -y for CLI side to side