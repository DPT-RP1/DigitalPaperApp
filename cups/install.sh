#!/bin/bash
sudo apt-get install libcups2-dev
sudo apt-get install cups

sudo mkdir -p /usr/lib/cups/backend
sudo mkdir -p /usr/share/cups/model
sudo mkdir -p /etc/cups
sudo mkdir -p /var/spool/cups-pdf-dpt/
rm cups-pdf-dpt
gcc -O9 -s -o cups-pdf-dpt cups-pdf.c -lcups
sudo cp cups-pdf-dpt /usr/lib/cups/backend/
sudo cp CUPS-PDF-DPT.ppd /usr/share/cups/model
sudo cp cups-pdf-dpt.conf /etc/cups/cups-pdf-dpt.conf

sudo chmod 700 /usr/lib/cups/backend/cups-pdf-dpt
sudo chmod 777 /var/spool/cups-pdf-dpt
sudo cp cups-pdf-dpt-post.sh /usr/share/cups/

echo "If you create the printer with the URL like this: cups-pdf://localhost, then it will be looking for a file cups-pdf-/localhost.conf Change the URL to cups-pdf:/ (it is a valid url after it's created, but you might not be able to use it during creation). It will then look for cups-pdf.conf"
echo "Removing any previous installation"
sudo lpadmin -x "DPT-RP1"
echo "Installing new printer at cups-pdf-dpt:/"
sudo lpadmin -p "DPT-RP1" -D "Sony Digital Paper" -v cups-pdf-dpt:/ -E -P /usr/share/cups/model/CUPS-PDF-DPT.ppd

# Check success
lpstat -t

# List printers and whcih one is the default printer
lpstat -p -d

sudo service cups restart

echo "Test print with: lp -d cups-pdf-dpt <path/to/txtfile> to see the file printed in /var/spool/cups-pdf-dpt"