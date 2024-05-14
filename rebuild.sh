#! /bin/bash
cp ~/.ssh/authorized_keys ./authorized_keys
docker rm snakeoil -f
docker rmi snakeoil:latest -f
docker build -t snakeoil:latest .
docker run -d -p 80:4242 --name snakeoil snakeoil:latest
rm authorized_keys
docker logs -f snakeoil
