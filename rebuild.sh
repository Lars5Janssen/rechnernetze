#! /bin/bash
docker rm snakeoil
docker rmi snakeoil:latest
cd
docker build -t snakeoil:latest -f rechnernetze/Dockerfile .
cd -
docker run -d -p 80:4242 --name snakeoil snakeoil:latest
