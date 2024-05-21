#!/bin/bash
service ssh start > /dev/null
rm -rf /rechnernetze
git clone -b redesign_praktikum1 https://github.com/Lars5Janssen/rechnernetze.git /rechnernetze > /dev/null
cd /rechnernetze
./gradlew fatJar > /dev/null
cd /rechnernetze/build/libs
git log -n 1
java -cp rechnernetze-1.0-SNAPSHOT.jar server.Server
