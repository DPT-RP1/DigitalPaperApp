# Reverse engineering
This file holds various notes on the DPT architecture

## Where are the PDF actually stored 
They are stored in `/mnt/sdcard/dp_user_storage/file`, accessible via ADB on a running DPT
Each of the pdfs are stored in a folder named after its UUID:
```bash
root@FPX-1010:/mnt/sdcard/dp_user_storage/file # ls **/** | busybox head -10   
007cadfd-f313-448d-a011-1ea46ee92a10/007cadfd.pdf
016297c4-0347-433f-9bbd-2f437e18c8a8/016297c4.pdf
017dd7d7-7092-4e2b-92f1-a396d9b600ee/017dd7d7.pdf
018bc84e-0058-4926-85ba-c67c99077d50/018bc84e.pdf
01e2e831-f9a4-4536-b6c0-8486376dee58/01e2e831.pdf
```
The exact structure is therefore:
UUID1-UUID2-UUID3-UUID4-UUID5/UUID1.pdf

This is mapped probably to a database view, which can retrieve the documents directly by their id with a simple fs lookup.

## What is the SDK version to target when building applications
```bash
root@FPX-1010:/data/data # getprop ro.build.version.sdk 
22
root@FPX-1010:/data/data # wm size
Physical size: 1650x2200
root@FPX-1010:/data/data # wm density
Physical density: 160
root@FPX-1010:/data/data # cat /proc/meminfo | grep MemTotal
MemTotal:        1983232 kB
```