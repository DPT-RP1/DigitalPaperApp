#!/bin/bash
sudo apt install git-buildpackage help2man maven dpkg
sudo apt-get install fuse libfuse-dev

pkg_name=dpt
version="$(cat VERSION)"
root_folder="${pkg_name}_${version}"

rm -rf "${root_folder}"
mkdir "${root_folder}"

mkdir -p "${root_folder}/usr/share/dpt"
mkdir -p "${root_folder}/usr/bin"
mkdir -p "${root_folder}/usr/share/doc/dpt/"
mkdir -p "${root_folder}/usr/share/man/man1/"

echo "Building jar..."
cd ..
mvn clean package 1>/dev/null 2>/dev/null || (echo "Build failed...";exit)
cd debian || exit

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
cp changelog changelog.backup
gbp dch --ignore-branch --since=1c23aca0070f0f7a9d9f064b2d172165eb8060c2 -R --spawn-editor=never --git-author --customizations=/usr/share/doc/git-buildpackage/examples/wrap_cl.py

gzip -9 changelog -c > changelog.gz

mv changelog.gz "${root_folder}/usr/share/doc/dpt/changelog.Debian.gz"

echo "Building documentation"
help2man -N -n "A simple Sony Digital Paper CLI manager" "java -jar ${root_folder}/usr/share/dpt/${jar_name}.jar --help" > dpt.1
gzip -9 dpt.1 -c > dpt.1.gz
mv dpt.1.gz "${root_folder}/usr/share/man/man1/"

echo "Building package"
dpkg-deb --root-owner-group --build "${root_folder}"

echo "Cleaning up"
rm -rf "${root_folder}"
mv changelog.backup changelog
rm ../cups/cups-pdf-dpt
rm dpt.1

echo "Debian package built, now installing"
sudo dpkg -r dpt
sudo dpkg -i dpt_1.0-1.deb

lintian ./dpt_1.0-1.deb --no-tag-display-limit