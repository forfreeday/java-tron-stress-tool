#!/bin/bash

MAX_STOP_TIME=30
APP_NAME='FullNode'
ip=`ip add |grep eth0|grep inet |awk -F ' ' '{print $2}' |awk -F '/' '{print $1}'`

checkPid() {
  pid=$(ps -ef | grep -v start | grep $APP_NAME | grep -v grep | awk '{print $2}')
}

stopService() {
  count=1
  while [ $count -le $MAX_STOP_TIME ]; do
    checkPid
    pidArr=($pid)
    if [ ${#pidArr[@]} -gt 1 ];then
       for i in ${!pidArr[@]}
       do
          kill -15 ${pidArr[$i]}
       done
       break
    fi

    if [ $pid ]; then
      kill -15 $pid
      sleep 1
    else
      echo "[info]: java-tron stop, ip: " $ip
      return
    fi
    count=$(($count + 1))
    if [ $count -eq $MAX_STOP_TIME ]; then
      kill -9 $pid
      sleep 1
    fi
  done
  sleep 5
}

stopService
