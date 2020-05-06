#!/bin/bash
brew tap homebrew/cask-versions
brew cask install osxfuse
brew cask install java
export JAVA_8_HOME=$(/usr/libexec/java_home -v1.8)
export JAVA_11_HOME=$(/usr/libexec/java_home -v11)

alias java8='export JAVA_HOME=$JAVA_8_HOME'
alias java11='export JAVA_HOME=$JAVA_11_HOME'

java11

cd ..
mvn clean compile package ${1:-}

mkdir -p ~/.local/share/dpt
mkdir -p ~/.local/bin
cp target/DigitalPaperApp*.jar ~/.local/share/dpt/DigitalPaperApp.jar
cp install/dpt ~/.local/bin/dpt
chmod +x ~/.local/bin/dpt
export PATH=$PATH:~/.local/bin

echo "The dpt command will be installed in ~/.local/bin, add it to your PATH if not done already with export PATH=\$PATH:~/.local/bin"