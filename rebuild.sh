#! /bin/bash
cp ~/.ssh/authorized_keys ./authorized_keys
docker rm snakeoil -f
docker rmi snakeoil:latest -f
docker build -t snakeoil:latest .
docker run -d -p 80:4242 --name snakeoil snakeoil:latest
clear
rm authorized_keys
docker exec snakeoil ./gitlog.sh
docker logs -f --since 0m snakeoil 
