#!/bin/bash

cwd=$(pwd)
cd ..

./patch.sh ./official ./patch_adb/adb.patch adb

cd "$cwd" || exit
cd ..

cp ./patch_adb/adbd ./adb/initramfs/sbin/

chmod 771 ./adb/initramfs/data
chmod 755 ./adb/initramfs/init.pxa1908.usb.rc
chmod 755 ./adb/initramfs/init
chmod 755 ./adb/initramfs/init.usb.rc
chmod 755 ./adb/initramfs/sbin/healthd
chmod 755 ./adb/initramfs/sbin/mount_ddat.sh

cd ./patch_adb || exit