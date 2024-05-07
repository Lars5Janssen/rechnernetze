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
cmd: cat /etc/group | grep <userName>    (in diesem fall ist userName = padawan)
