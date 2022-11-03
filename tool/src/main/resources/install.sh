#!/usr/bin/env bash

download() {
  local url=$1
  if type wget >/dev/null 2>&1; then
    sudo wget -P $2 --no-check-certificate -q $url
  elif type curl >/dev/null 2>&1; then
    sudo curl -o $2 -OLJ $url
  else
    echo 'info: no exists wget or curl, make sure the system can use the "wget" or "curl" command'
  fi
}

user=`whoami`
group=`groups`
download "https://raw.githubusercontent.com/forfreeday/java-tron-stress-tool/main/tool/src/main/resources/tron-deploy.sh" /usr/bin/
sudo mv /usr/bin/tron-deploy.sh /usr/bin/tron-deploy
sudo chmod +x /usr/bin/tron-deploy
sudo chown $user:$groups /usr/bin/tron-deploy
