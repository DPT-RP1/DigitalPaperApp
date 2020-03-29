# dpt-rp1-java
Java port of https://github.com/janten/dpt-rp1-py

Script to manage Sony DPT-RP1 without the Digital Paper App. This repository includes a Java library and a command line utility to manage documents on the DPT-RP1. Barely tested. May not work for Sony's other digital paper readers.

## Installation
If you want a practical CLI, there now is a proper Python package, so you may just run:

```
pip3 install dpt-rp1-py
```

Installing the package also installs the command line utilities `dptrp1` and `dptmount`.


### From Source
To install the library from the sources, clone this repository, then run `mvn clean install` from the root directory.

## Using the command line utility
You need java 11 and maven.
```
mvn exec:java -Dexec.mainClass="net.sony.dpt.DigitalPaperApp" -Dexec.args="command [arguments]"
```

To see if you can successfully connect to the reader, try the command `list-documents`. If you have Sony's Digital Paper App installed, this should work without any further configuration. If this fails, register your reader with the app using `register`.

### Supported commands
Note that the root path for DPT-RP1 is `Document/`. Example command to download a document `file.pdf` from the root folder ("System Storage") of DPT-RP1: `download Document/file.pdf ./file.pdf`. Example command to upload a document `file.pdf` to a folder named `Articles` on DPT-RP1: `upload ./file.pdf Document/Articles/file.pdf`

### Registering the DPT-RP1
The DPT-RP1 uses SSL encryption to communicate with the computer.  This requires registering the DPT-RP1 with the computer, which results in two pieces of information -- the client ID and the key file -- that you'll need to run the script. You can get this information in three ways.

#### Registering without the Digital Paper App
This method requires your DPT-RP1 and your computer to be on the same network segment via WiFi, Bluetooth or a USB connection. The USB connection works on Windows and macOS but may not work on a Linux machine. If your WiFi network is not part of the "Saved Network List" (for example if you don't have the app), you can still use the DPT-RP1 as a WiFi access point and connect your computer to it.

```
register
```

The tool can generally figure out the correct IP address of the device automatically, but you may also specify it with the `--addr <address>` option. If you're on WiFi, go to _Wi-Fi Settings_ on the device and tap the connected network to see the device's address. If you use a Bluetooth connection, it's likely _172.25.47.1_. You can also try the hostname _digitalpaper.local_. Use the _register_ command like seen below, substituting the IP address of the device.

```
--addr 10.0.0.1 register
```

If you get an error, wait a few seconds and try again. Sometimes it takes two or three tries to work.

## Mounting as a file system
Unsupported

#### Finding the private key and client ID on Windows

If you have already registered on Windows, the Digital Paper app stores the files in _Users/{username}/AppData/Roaming/Sony Corporation/Digital Paper App/_. You'll need the files _deviceid.dat_ and _privatekey.dat_.

#### Finding the private key and client ID on macOS

If you have already registered on macOS, the Digital Paper app stores the files in _$HOME/Library/Application Support/Sony Corporation/Digital Paper App/_. You'll need the files _deviceid.dat_ and _privatekey.dat_.

#### What works
* Reading files
* Moving files (both rename and move to different folder)
* Uploading new files
* Deleting files and folders 

#### What does not work
* Currently there is no caching, therefore operations can be slow as they require uploading or downloading from the 
device. However, this avoids having to resolve conflicts if a document has been changed both on the Digital Paper and
the caching directory.

## Usage

Else, over Wifi:

```
# Try multiple times
--addr 192.168.0.107 register

--addr 192.168.0.107 list-documents
```