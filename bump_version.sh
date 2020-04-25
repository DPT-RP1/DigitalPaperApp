#!/bin/bash

# This script sets the version before a release
VERSION="$1"

# 1. Maven: pom.xml:<version>X.X.X</version>
sed -i -e "s/<version><!-- Auto Generated -->.*</<version><!-- Auto Generated -->${VERSION}</" pom.xml

# 2. Debian: VERSION file
echo "${VERSION}" > debian/VERSION

# 3. Debian: control:Version: X.X.X
sed -i -e "s/Version: .*/Version: ${VERSION}/" debian/DEBIAN/control

