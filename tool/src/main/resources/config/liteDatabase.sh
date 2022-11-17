#!/usr/bin/env bash
databaseDir=liteDatabase/
IP="http://47.89.178.46/"
# example: http://47.89.178.46/backup20221117/
date=``
# 下载轻节点数据
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

download "$url" "${databaseDir}LiteFullNode_output-directory.tgz"
tar -zxvf LiteFullNode_output-directory.tgz
