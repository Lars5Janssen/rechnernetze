#!/bin/bash
service ssh start
java -cp /build/libs/rechnernetze-1.0-SNAPSHOT.jar server.Server
