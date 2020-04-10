# dpt
Inspired by https://github.com/janten/dpt-rp1-py

Script to manage Sony DPT-RP1 without the Digital Paper App. 

This repository includes:
 - a Java library 
 - a command line utility to manage documents on the DPT-RP1
 - a CUPS driver to use the DPT as a printer
 - a server to proxy whiteboard refreshes to a webpage (dpt whiteboard-html) you can share with people
 - a translation (more and more complete) of the official endpoint documentation at doc/endpoints.json
 - a packaging script to create a .deb package at debian/makedeb.sh
 
## How you can contribute
 - I'd love to have people testing this on Windows
 - Building an rpm and a mac pkg
 - Testing the CUPS driver on many more archs
 
## Installation
You can install on debian using the deb package at https://github.com/xpierrohk/DigitalPaperApp/releases

```
# apt install dpt_<version>.deb 
```

Run dpt or man dpt to get more information.

### From Source
To install the library from the sources, clone this repository, then run `mvn clean install` from the root directory.
The use the script in install/linux.sh to setup up the dpt command in your home folder.

## Using the command line utility
Type dpt --help to find all the possible options.

### Supported commands
```$xslt
dpt command [parameters] [-options] [-addr] [-serial]
        register 
                Starts the pairing process with the Digital Paper
        ping 
                Tests the connection with the Digital Paper
        sync local-sync-folder [-dryrun] 
                Synchronizes a local folder with the Digital paper
        list-documents 
                Lists all documents
        document-info 
                Prints all documents and their attributes, raw
        upload local-file [remote-file] 
                Sends a local file to the Digital Paper
        download 
        move source target 
        copy source target 
        new-folder remote-folder 
        delete-folder remote-file 
        delete remote-file 
        print local-file 
                Sends a pdf to the Digital Paper, and opens it immediately
        watch-print local-folder 
                Watches a folder, and print pdfs on creation/modification in this folder
        screenshot png-file 
        whiteboard 
                Shows a landscape half-scale projection of the digital paper, refreshed every second
        whiteboard-html 
                Opens a distribution server with /frontend path feeding the images fron the Digital Paper
        dialog title content button 
                Prints a dialog on the Digital Paper
        get-owner 
        set-owner 
        wifi-list 
        wifi-scan 
        wifi-add 
        wifi-del 
        wifi 
        wifi-enable 
        wifi-disable 
        battery 
                Shows the battery status informations
        storage 
                Shows the storage status informations
        update-firmware 
                BETA - NON FUNCTIONAL
        get url 
                Sends and display a GET request to the Digital Paper
        help 
                Prints this message

```

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

#### Finding the private key and client ID on Windows

If you have already registered on Windows, the Digital Paper app stores the files in _Users/{username}/AppData/Roaming/Sony Corporation/Digital Paper App/_. You'll need the files _deviceid.dat_ and _privatekey.dat_.

#### Finding the private key and client ID on macOS

If you have already registered on macOS, the Digital Paper app stores the files in _$HOME/Library/Application Support/Sony Corporation/Digital Paper App/_. You'll need the files _deviceid.dat_ and _privatekey.dat_.

## Usage

```
# Try multiple times
--addr 192.168.0.107 register
--addr 192.168.0.107 ping
```