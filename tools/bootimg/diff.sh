#!/bin/bash

# This is a convenience script to remeber the diff options
# $1 folder1
# $2 folder2

diff -qr "$1/initramfs/" "$2/initramfs/"
diff -ruN "$1/initramfs/" "$2/initramfs/" > initramfs.patch
# sudo apt install meld for a GUI
# diff -y for CLI side to side