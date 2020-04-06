#!/bin/bash
sudo apt-get install libcups2-dev
sudo apt-get install cups

root_folder=$1
cups_folder=../cups

mkdir -p "${root_folder}/usr/lib/cups/backend"
mkdir -p "${root_folder}/usr/share/cups/model"
mkdir -p "${root_folder}/etc/cups"
mkdir -p "${root_folder}/var/spool/cups-pdf-dpt"

echo "Compiling cups backend..."
rm "${cups_folder}/cups-pdf-dpt"
cd "${cups_folder}" || exit
gcc -O9 -s -o cups-pdf-dpt cups-pdf.c -lcups 1>/dev/null 2>/dev/null
cd ../deb || exit

echo "Packaging cups backend"
cp "${cups_folder}/cups-pdf-dpt" "${root_folder}/usr/lib/cups/backend/"
cp "${cups_folder}/CUPS-PDF-DPT.ppd" "${root_folder}/usr/share/cups/model"
cp "${cups_folder}/cups-pdf-dpt.conf" "${root_folder}/etc/cups/cups-pdf-dpt.conf"

chmod 700 "${root_folder}/usr/lib/cups/backend/cups-pdf-dpt"
cp "${cups_folder}/cups-pdf-dpt-post.sh" "${root_folder}/usr/share/cups/"
