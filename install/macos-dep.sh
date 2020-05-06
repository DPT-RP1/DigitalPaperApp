#!/bin/bash

# Dependencies in MacOS are quite heavy to build

brew tap homebrew/cask-versions
brew cask install osxfuse
brew install maven
brew cask install java
xcode-select --install
brew install gcc

export JAVA_8_HOME=$(/usr/libexec/java_home -v1.8)
export JAVA_11_HOME=$(/usr/libexec/java_home -v11)

alias java8='export JAVA_HOME=$JAVA_8_HOME'
alias java11='export JAVA_HOME=$JAVA_11_HOME'

java11

./macos.sh