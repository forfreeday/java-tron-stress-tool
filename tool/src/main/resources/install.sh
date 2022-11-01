#!/usr/bin/env bash

download() {
  local url=$1
  if type wget >/dev/null 2>&1; then
    wget -P $2 --no-check-certificate -q $url
  elif type curl >/dev/null 2>&1; then
    curl -o $2 -OLJ $url
  else
    echo 'info: no exists wget or curl, make sure the system can use the "wget" or "curl" command'
  fi
}

download "https://https://github.com/forfreeday/java-tron-stress-tool/blob/main/tool/src/main/resources/tron-develop.sh" /usr/bin/tron-develop
chmod +x /usr/bin/tron-develop