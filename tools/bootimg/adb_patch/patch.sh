#!/bin/bash

# $1 official bootimg extract folder

cwd=$(pwd)

cd "$1" || exit
cd ..

"$cwd"/../patch.sh ./official "$cwd"/adb.patch adb
cp "$cwd"/adbd ./adb/initramfs/sbin/

cd "$cwd" || exit