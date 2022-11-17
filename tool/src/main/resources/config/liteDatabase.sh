#!/usr/bin/env bash

# nohup sh liteDatebase.sh 2>&1 &
databaseDir=liteDatabase/
IP="http://47.89.178.46"
backupDate=`date +%Y%m%d --date="-1 day"`
url="${IP}/backup${backupDate}/LiteFullNode_output-directory.tgz"
# 下载轻节点数据
download() {
  local url=$1
  if type wget >/dev/null 2>&1; then
    wget -P $2 --no-check-certificate q $url
  elif type curl >/dev/null 2>&1; then
    curl -o $2 -OLJ $url
  else
    echo 'info: no exists wget or curl, make sure the system can use the "wget" or "curl" command'
  fi
}
download "$url" "${databaseDir}"
