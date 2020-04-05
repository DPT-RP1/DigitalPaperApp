#!/bin/bash
cd ..
mvn clean compile package

echo "The dpt command will be installed in ~/.local/bin, add it to your PATH if not done already"
mkdir -p ~/.local/share/dpt
mkdir -p ~/.local/bin
cp target/DigitalPaperApp*.jar ~/.local/share/dpt/DigitalPaperApp.jar
cp install/dpt ~/.local/bin/dpt
chmod +x ~/.local/bin/dpt
export PATH=$PATH:~/.local/bin

cd cups
./install.sh
