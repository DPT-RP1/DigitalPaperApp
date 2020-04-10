#!/bin/bash
sudo apt install git-buildpackage

pkg_name=dpt
version="$(cat VERSION)"
root_folder="${pkg_name}_${version}"

rm -rf "${root_folder}"
mkdir "${root_folder}"

mkdir -p "${root_folder}/usr/share/dpt"
mkdir -p "${root_folder}/usr/bin"
mkdir -p "${root_folder}/usr/share/doc/dpt/"

echo "Building jar..."
cd ..
mvn clean package 1>/dev/null 2>/dev/null || (echo "Build failed...";exit)
cd deb || exit

jar_name=DigitalPaperApp
jar_version=1.0-SNAPSHOT

echo "Building main executable"
cp "../target/${jar_name}-${jar_version}.jar" "${root_folder}/usr/share/dpt/${jar_name}.jar"
cp "dpt" "${root_folder}/usr/bin/dpt"
chmod +x "${root_folder}/usr/bin/dpt"

echo "Building cups driver"
./cups.sh "${root_folder}"

echo "Writing package metadata"
mkdir "${root_folder}/DEBIAN"

#TODO: filtering for the version ?
cp -R DEBIAN "${root_folder}/"
cp copyright "${root_folder}/usr/share/doc/dpt/"

echo "Building changelog"
cp changelog changelog.bak
gbp dch --ignore-branch --since=1c23aca0070f0f7a9d9f064b2d172165eb8060c2 -R --full --spawn-editor=never --git-author

cp changelog.gz "${root_folder}/usr/share/doc/dpt/changelog.Debian.gz"
cp changelog.gz "${root_folder}/usr/share/doc/dpt/changelog.gz"

echo "Building package"
dpkg-deb --root-owner-group --build "${root_folder}"

echo "Cleaning up"
rm -rf "${root_folder}"
cp changelog.bak changelog
echo "Debian package built, now installing"
sudo dpkg -r dpt
sudo dpkg -i dpt_1.0-1.deb

lintian ./dpt_1.0-1.deb --no-tag-display-limit