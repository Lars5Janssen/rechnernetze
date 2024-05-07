# Rechnernetze
RN-SOSE 2024
## How to ssh Tunnel
**Your machine to proxy** \
`ssh -L 8080:141.22.40.11:8081 infwarxxx@141.22.40.11`

**Proxy to Padawan** \
`ssh -L 141.22.40.11:8081:141.22.11.123:80 padawan@141.22.11.123`

## How to Backup
Use SCP to secure our work
1. tar the 23w-main directory\
   `tar -zcvf <NameofExport.tar.gz [Path to file you want to untar]`
3. scp to the proxy\
   `scp <tarfile>  infwarxx@141.22.40.11:~/`
4. scp to your own machine (see above)
5. scp from remote to local (from local terminal)
   `scp infwarxx@141.22.40.11:~/[file to be transferred] ~/[local dest path]`
7. untar\
  `tar -xzvf [File you want to unzip]`
8. Add to repository

## Wie ich erkenne welche root rechte ich habe
cmd: cat /etc/group | grep "userName"    (in diesem fall ist userName = padawan)

## Hilfestellung / CheatSheet
### Image bauen
```bash
# cd $ORDER_MIT_Dockerfile
docker build -t snakeoil:latest .
```

### Image im Container starten
```bash
docker run -d -p 80:80 -p 443:443 -p 127.0.0.1:2222:222 --name snakeoil snakeoil:latest 
``` 

### Terminal im Container verwenden
```bash
docker exec -ti snakeoil bash -c 'cd "/userdata/webportal" && echo Hallo ${CI_PROJECT_NAME} && bash' 
``` 

### Container stoppen
```bash
docker stop snakeoil
```

### Container löschen
```bash
docker rm snakeoil
```

### Images löschen / Speicherplatz schaffen
```bash
# Vorhandene Images listen
docker images
# Ausgewähltes Image löschen
docker rmi $IMAGE_ID
```
