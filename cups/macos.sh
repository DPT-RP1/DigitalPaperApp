#!/bin/bash
echo "If you see Operation not permitted, grant bash Full Disk Access"

sudo mkdir -p /usr/libexec/cups/backend
sudo mkdir -p /usr/share/cups/model
sudo mkdir -p /etc/cups
sudo mkdir -p /var/spool/cups-pdf-dpt/
mkdir -p ${HOME}/.dpt/print
chmod 701 ${HOME}
chmod 777 ${HOME}/.dpt/print/
rm cups-pdf-dpt
gcc -O3 -o cups-pdf-dpt cups-pdf.c -lcups
sudo cp cups-pdf-dpt /usr/libexec/cups/backend/

sudo sh -c 'gzip < CUPS-PDF-DPT.ppd  > /Library/Printers/PPDs/Contents/Resources/CUPS-PDF-DPT.ppd.gz'
sudo cp cups-pdf-dpt_macos.conf /etc/cups/cups-pdf-dpt.conf

sudo chmod 700 /usr/libexec/cups/backend/cups-pdf-dpt
sudo cp cups-pdf-dpt-post.sh /etc/cups/

echo "If you create the printer with the URL like this: cups-pdf://localhost, then it will be looking for a file cups-pdf-/localhost.conf Change the URL to cups-pdf:/ (it is a valid url after it's created, but you might not be able to use it during creation). It will then look for cups-pdf.conf"
echo "Removing any previous installation"
sudo lpadmin -x "DPT-RP1"
echo "Installing new printer at cups-pdf-dpt:/"
sudo lpadmin -p "DPT-RP1" -D "Sony Digital Paper" -v cups-pdf-dpt:/ -E -P /Library/Printers/PPDs/Contents/Resources/CUPS-PDF-DPT.ppd.gz

# Check success
lpstat -t

# List printers and whcih one is the default printer
lpstat -p -d

echo "Test print with: lp -d cups-pdf-dpt <path/to/txtfile> to see the file printed in /var/spool/cups-pdf-dpt"