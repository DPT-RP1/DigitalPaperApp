#!/bin/bash
cd ..
mvn clean compile package

echo "The dpt command will be installed in ~/.local/bin, add it to your PATH if not done already"
mkdir -p ~/.local/share/dpt
mkdir -p ~/.local/bin
cp target/DigitalPaperApp*.jar ~/.local/share/dpt/DigitalPaperApp.jar
echo "java -jar ~/.local/share/dpt/DigitalPaperApp.jar \"\$@\"" > ~/.local/bin/dpt
chmod +x ~/.local/bin/dpt
export PATH=$PATH:~/.local/bin
