#!/bin/bash
service ssh start > /dev/null
rm -rf /rechnernetze
git clone -q https://github.com/Lars5Janssen/rechnernetze.git /rechnernetze > /dev/null
cd /rechnernetze
git log --format=format:"%an --> %Cgreen %B"   -n 1
./gradlew fatJar > /dev/null
cd /rechnernetze/build/libs
echo ""
echo ""
echo ""
java -cp rechnernetze-1.0-SNAPSHOT.jar server.Server
