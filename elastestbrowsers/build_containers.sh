#!/bin/bash -x
set -e


FIREFOX_OLD_VERSIONS="56.0 57.0 58.0"

WORKDIR=$PWD/workdir
[ -d $WORKDIR ] || mkdir -p $WORKDIR
rm $WORKDIR/* || true

# Get browsers versions
docker run -t --rm -v $WORKDIR:/workdir elastestbrowsers/utils-get_browsers_version:1.0
. $WORKDIR/versions.txt

# Build base image
pushd base
docker build -t elastestbrowsers/utils-x11-base:1.1 .
popd

# Copy drivers
cp -p workdir/geckodriver firefox/image/selenoid/geckodriver
cp -p workdir/selenoid_linux_amd64 firefox/image/selenoid/selenoid_linux_amd64

# Firefox
pushd firefox
sed "s/VERSION/$FIREFOX_VER/g" image/selenoid/browsers.json.templ > image/selenoid/browsers.json
docker build --build-arg VERSION=$FIREFOX_PKG -t elastestbrowsers/firefox:$FIREFOX_VER -t elastestbrowsers/firefox:latest -f Dockerfile.firefox .
rm image/selenoid/browsers.json

# Firefox Beta
sed "s/VERSION/beta/g" image/selenoid/browsers.json.templ > image/selenoid/browsers.json.beta
docker build -t elastestbrowsers/firefox:beta -f Dockerfile.firefox.beta .
rm image/selenoid/browsers.json.beta

# Firefox Nightly
sed "s/VERSION/nightly/g" image/selenoid/browsers.json.templ > image/selenoid/browsers.json.nightly
docker build -t elastestbrowsers/firefox:nightly -f Dockerfile.firefox.nightly .
rm image/selenoid/browsers.json.nightly
popd

# Firefox old versions
for V in $FIREFOX_OLD_VERSIONS
do
sed "s/VERSION/$V/g" image/selenoid/browsers.json.templ > image/selenoid/browsers.json
docker build --build-arg VERSION=$V -t elastestbrowsers/firefox:$V -f Dockerfile.firefox .
rm image/selenoid/browsers.json
done

# cleaning
rm firefox/image/selenoid/geckodriver
rm firefox/image/selenoid/selenoid_linux_amd64

# Copy drivers
cp -p workdir/chromedriver chrome/image/selenoid/chromedriver
cp -p workdir/selenoid_linux_amd64 chrome/image/selenoid/selenoid_linux_amd64

# Chome
pushd chrome
sed "s/VERSION/$CHROME_VER/g" image/selenoid/browsers.json.templ > image/selenoid/browsers.json
docker build --build-arg VERSION=$CHROME_PKG -t elastestbrowsers/chrome:$CHROME_VER -t elastestbrowsers/chrome:latest -f Dockerfile.chrome .
rm image/selenoid/browsers.json

# Chrome Beta
sed "s/VERSION/beta/g" image/selenoid/browsers.json.templ > image/selenoid/browsers.json.beta
docker build -t elastestbrowsers/chrome:beta -f Dockerfile.chrome.beta .
rm image/selenoid/browsers.json.beta

# Chrome Unstable
sed "s/VERSION/unstable/g" image/selenoid/browsers.json.templ > image/selenoid/browsers.json.unstable
docker build -t elastestbrowsers/chrome:unstable -f Dockerfile.chrome.unstable .
rm image/selenoid/browsers.json.unstable
popd

# cleaning
rm chrome/image/selenoid/chromedriver
rm chrome/image/selenoid/selenoid_linux_amd64

docker images

