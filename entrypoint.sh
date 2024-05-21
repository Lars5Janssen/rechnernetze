#!/bin/bash
service ssh start
rm -rf /rechnernetze
git clone -b redesign_praktikum1 https://github.com/Lars5Janssen/rechnernetze.git /rechnernetze
cd /rechnernetze
./gradlew fatJar
git log | cat
cd /rechnernetze/build/libs
java -cp rechnernetze-1.0-SNAPSHOT.jar server.Server
