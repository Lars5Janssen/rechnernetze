#!/bin/bash
service ssh start
rm -rf /rechnernetze
git clone https://github.com/Lars5Janssen/rechnernetze.git /rechnernetze
cd rechnernetze
gradle fatJar
tail -f /dev/null
