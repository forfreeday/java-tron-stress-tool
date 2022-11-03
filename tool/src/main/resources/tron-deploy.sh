#!/usr/bin/env bash
#set -e
#set -x
# [github]

repositoryUrl='https://github.com/tronprotocol/java-tron.git'
branch='build_pri_chain'
isCloneCode=false
isPullCode=false
# 代码构建
isBuildCode=false

# 是否覆盖配置文件
isOverrideConfig=false

# [工作目录]
# 本地java-tron目录
workspace=/data/tron-build/
javaTronDir="${workspace}/java-tron/"
localConfigDir="${workspace}/config/"
deployConfigDir="${localConfigDir}/deploy"

# [远程工作目录]
# 目标机器java-tron目录
remoteProjectDIR=/data/test-deploy

# [SR节点]
# 构建推送SR节点
isSendWitness=false
isSendAll=false
witnessNet=(
)

# [FullNode节点]
# 构建推送FullNode
isSendFullNode=false
# 重启远程FullNode节点
isRestartFullNode=false
fullnodeNet=(
)

# [数据库]
# 发送最新的数据库
isSendNewDatabase=false
# 本地数据库存放目录
isUseLocalDatabase=false
localDatabaseDir="${workspace}/database"












###############################################[节点控制]###################################################
# [节点控制]
isInit=false

# 启动
isStart=false
isStop=false
controlType=''
controlNodes=''
# 重启远程SR节点
isRestartWitness=false
# 重启SR、FullNode
isRestartAll=false
# 获取节点消息
nodeInfos=''

# int
localCommandPath='/usr/bin/'
localCommand='tron-deploy'
localShell='tron-deploy.sh'

###############################################[构建部分]###################################################

cloneCode() {
  echo "[info] cloning"
  cd $workspace || { echo "Failure"; exit 1; }
  git clone -b $branch $repositoryUrl
}

pullCode() {
  echo "[info] pull code"
  cd $workspace || { echo "Failure"; exit 1; }
  git branch -v
  git pull
}

buildCode() {
  if [ -d "$javaTronDir" ]; then
    echo "[info] build code, current branch: " $branch
    cd $javaTronDir || { echo "Failure"; exit 1; }
    git reset
    git pull
    git checkout $branch
    ./gradlew clean build -x test -x check
  fi
}

setLocalwitness() {
  echo "[info] set local witness"
}

# 创建默认配置文件，在 init 时触发
createConfigFile() {
   configFile=$(sed -n "3, 60p" "$localCommandPath/$localCommand")
   if [ ! -d $localConfigDir ]; then
      mkdir -p $localConfigDir
   fi
   echo "$configFile" > $localConfigDir/deployment.conf

   if [ ! -d $deployConfigDir ]; then
      mkdir -p $deployConfigDir/sr
      mkdir -p $deployConfigDir/fullnode
      # download sr config

      download https://raw.githubusercontent.com/tronprotocol/java-tron/build_pri_chain/framework/src/main/resources/deploy/sr/config-stress.conf $deployConfigDir/sr
      download https://raw.githubusercontent.com/tronprotocol/java-tron/build_pri_chain/framework/src/main/resources/deploy/sr/start.sh $deployConfigDir/sr
      download https://github.com/tronprotocol/java-tron/blob/build_pri_chain/framework/src/main/resources/deploy/sr/stop.sh $deployConfigDir/sr

      download https://raw.githubusercontent.com/tronprotocol/java-tron/build_pri_chain/framework/src/main/resources/deploy/fullnode/config-stress.conf $deployConfigDir/fullnode
      download https://raw.githubusercontent.com/tronprotocol/java-tron/build_pri_chain/framework/src/main/resources/deploy/fullnode/start.sh $deployConfigDir/fullnode
      download https://github.com/tronprotocol/java-tron/blob/build_pri_chain/framework/src/main/resources/deploy/fullnode/stop.sh $deployConfigDir/fullnode
   fi
}

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

# init java-tron tool
initTool() {
   echo "[info] init java-tron test tool"
   # 清空旧代码目录
   if [ -d "$workspace" ]; then
       rm -rf $workspace
   fi
   mkdir -p $workspace
   mkdir -p $localDatabaseDir
   sudo cp "$localShell" "$localCommandPath/$localCommand"
   sudo chmod +x "$localCommandPath/$localCommand"
   sudo chown

   createConfigFile

   # clone project
   isCloneCode=true
   isBuildCode=true

   #  #setLocalwitness
   #  must send database, config
   isOverrideConfig=false
   isSendNewDatabase=false
}

# 推送 SR 的java-tron代码、配置文件 到指定机器节点
# 推送内容：
#   FullNode.jar
#   start.sh
#   stop.sh
#   config.conf -> config-stress.conf
sendWitnessNode() {

  cd $workspace || { echo "Failure"; exit 1; }
  cp $javaTronDir/build/libs/FullNode.jar .

  for node in "${witnessNet[@]}"; do
    {
    ssh -p 22008 java-tron@$node "mkdir -p $remoteProjectDIR"
    # stop
    ssh -p 22008 java-tron@$node "source ~/.bash_profile; sh $remoteProjectDIR/stop.sh; sleep 4"

    # delete old data
    ssh -p 22008 java-tron@$node "cd $remoteProjectDIR; \
        rm -rf output-directory; \
        mkdir -p liteDatabase/output-directory; \
        rm -rf FullNode.jar;"

    # 推送 java-tron
    tar -c FullNode.jar |pigz |ssh -p 22008 java-tron@$node "gzip -d|tar -xC $remoteProjectDIR"
    # 推送配置文件
    if [[ "$isOverrideConfig" = true ]]; then
       echo "[info] send config.conf"
       scp -P 22008 $deployConfigDir/sr/config.conf_$node java-tron@$node:$remoteProjectDIR/config-stress.conf
    fi
    scp -P 22008 $deployConfigDir/sr/start.sh java-tron@$node:$remoteProjectDIR/start.sh
    scp -P 22008 $deployConfigDir/sr/stop.sh java-tron@$node:$remoteProjectDIR/stop.sh
    echo "[info] send FullNode.jar, start.sh, stop.sh to ${node} completed"
    echo "[info] send witness: $node"
    # backup log
    backup_logname="`date +%Y%m%d%H%M%S`_backup.log"
    ssh -p 22008 java-tron@$node "mv $remoteProjectDIR/logs/tron.log $remoteProjectDIR/logs/$backup_logname"
    echo "[info] backup log name:  $backup_logname"

    # 使用远程机器上的备份数据库
    if [[ "$isUseLocalDatabase" = 'true' ]];then
       echo "[info] copy output-directory to $node"
       ssh -p 22008 java-tron@$node "cp -r $remoteProjectDIR/liteDatabase/output-directory/ $remoteProjectDIR"
       echo "[info] copy database to $node completed"
    fi

    # start
    echo "[info] start java-tron"
    ssh -p 22008 java-tron@$node "cd $remoteProjectDIR && sh start.sh"
   }&
   done
   wait

  # 并发发送数据库
  if [ "$isSendNewDatabase" = true ]; then
    echo "[info] send new database"
    sendDatabaseByNodes "${witnessNet[@]}"
    #ssh -p 22008 java-tron@$node "cp -r $remoteProjectDIR/liteDatabase/output-directory/ $remoteProjectDIR"
    echo "[info] copy database completed"
  fi
}

# 推送 FullNode 代码、配置文件 到指定机器节点
# FullNode 和 SR 使用的配置文件不同，由专门的两台机器部署
sendFullNode() {
  cd $workspace || { echo "Failure"; exit 1; }
  echo '[info] sendFullNode'
  cp $javaTronDir/build/libs/FullNode.jar .

  # 并发发送数据库
  if [ "$isSendNewDatabase" = true ]; then
    echo "[info] send new database"
    sendDatabaseByNodes "${fullnodeNet[@]}"
    #ssh -p 22008 java-tron@$node "cp -r $remoteProjectDIR/liteDatabase/output-directory/ $remoteProjectDIR"
    echo "[info] copy database completed"
  fi

  for node in "${fullnodeNet[@]}"; do
    {
    ssh -p 22008 java-tron@$node "mkdir -p $remoteProjectDIR"
    # stop
    ssh -p 22008 java-tron@$node "source ~/.bash_profile; \
       sh $remoteProjectDIR/stop.sh; \
       mkdir -p $remoteProjectDIR/liteDatabase/output-directory; \
       cd $remoteProjectDIR;
       rm -rf FullNode.jar
       "

    # 推送 FullNode.jar
    tar -c FullNode.jar |pigz |ssh -p 22008 java-tron@$node "gzip -d|tar -xC $remoteProjectDIR"
    # config.conf 通过配置文件
    if [[ "$isOverrideConfig" = true ]]; then
       echo "[info] send config.conf"
       scp -P 22008 $deployConfigDir/fullnode/config.conf_$node java-tron@$node:$remoteProjectDIR/config-stress.conf
    fi
    scp -P 22008 $deployConfigDir/fullnode/start.sh java-tron@$node:$remoteProjectDIR/start.sh
    scp -P 22008 $deployConfigDir/fullnode/stop.sh java-tron@$node:$remoteProjectDIR/stop.sh
    echo "[info]: send FullNode.jar, config.conf and start.sh to ${node} completed"
    echo "[info]: send witness: " $node
    # backup log
    backup_logname=$(date +%Y%m%d%H%M%S)"_backup.log"
    ssh -p 22008 java-tron@"$node" "mv $remoteProjectDIR/logs/tron.log $remoteProjectDIR/logs/$backup_logname"
    echo "backup log name: $backup_logname"

    # 使用远程机器上的备份数据库
    if [[ "$isUseLocalDatabase" = 'true' ]];then
       echo "[info] copy output-directory to $node"
       ssh -p 22008 java-tron@$node "cp -r $remoteProjectDIR/liteDatabase/output-directory/ $remoteProjectDIR"
       echo "[info] copy database to $node completed"
    fi
    # start
    ssh -p 22008 java-tron@$node "sh $remoteProjectDIR/start.sh"
   }&
   done
   wait
}

# 推送数据库
# isSendNewDatabase:
#   true: 推送完整数据
#   false: 使用远程机器上的备份数据库
sendDatabaseByNodes() {
  # 是否推送本地数据库 到 远程机器节点
  local nodes=("$@")
  if [ "$isSendNewDatabase" = true ]; then
    # 推送本机当前数据到 远程节点
    for node in "${nodes[@]}"; do
    {
      echo "[info] sending to: $node"
      ssh -p 22008 java-tron@"$node" "rm -rf $remoteProjectDIR/liteDatabase/output-directory && mkdir -p $remoteProjectDIR/liteDatabase/output-directory"
      # tar 默认读取相对路径
      cd $localDatabaseDir/ || { echo "Failure"; exit 1; }
      tar -c output-directory/ |pigz |ssh -p 22008 java-tron@"$node" "gzip -d|tar -xC $remoteProjectDIR/liteDatabase/" > /data/workspace/replay_workspace/server_workspace/${node}DBsend.log
      echo "[info] send database to: $node completed"
    }&
    done
    wait
  fi
}

sendDatabase() {
  local node=$1
  # 是否推送本地数据库 到 远程机器节点
  if [ "$isSendNewDatabase" = true ]; then
    # 推送本机当前数据到 远程节点
    echo "[info] send new database to:" $node
    ssh -p 22008 java-tron@$node "rm -rf $remoteProjectDIR/liteDatabase/output-directory && mkdir -p $remoteProjectDIR/liteDatabase/output-directory"
    # tar 默认读取相对路径
    cd $localDatabaseDir/ || { echo "Failure"; exit 1; }
    tar -c output-directory/ |pigz |ssh -p 22008 java-tron@$node "gzip -d|tar -xC $remoteProjectDIR/liteDatabase/" > $remoteProjectDIR/${node}DBsend.log
    echo "[info] send database to: $node completed"
  fi
}

startNodes() {
  local nodes=("$@")
  for node in "${nodes[@]}"; do
     {
     echo "[info]: start node: $node"
     ssh -p 22008 -Tq java-tron@"$node"<<"EOF"
         source ~/.bash_profile && cd $remoteProjectDIR
         # stop java-tron
     sh $remoteProjectDIR/stop.sh
     sleep 4
     # backup log
         sh $remoteProjectDIR/start.sh
         find $remoteProjectDIR/logs/ -mtime +10 -name "*" -exec rm -rf {} \;
     exit
 # 这个EOF必须在这个位置，左边不要有空格或缩进
EOF
    }&
    done
    wait
}

stopNodes() {
  local nodes=("$@")
  for node in "${nodes[@]}"; do
     {
     echo "[info]: stop node: $node"
     ssh -p 22008 -Tq java-tron@"$node"<<"EOF"
         source ~/.bash_profile && cd $remoteProjectDIR
         # stop java-tron
     sh $remoteProjectDIR/stop.sh
     sleep 4
     exit
 # 这个EOF必须在这个位置，左边不要有空格或缩进
EOF
    }&
    done
    wait
}

nodeControl() {
  local nodes=("$@")
  commands=''
  if [ "$controlType" = 'start' ];then
    commands="sh $remoteProjectDIR/start.sh"
  elif [ "$controlType" = 'stop' ];then
    commands="sh $remoteProjectDIR/stop.sh;sleep 4"
  fi

  for node in "${nodes[@]}"; do
     {
     echo "[info]: stop node: $node"
     ssh -p 22008 -Tq java-tron@"$node"<<"EOF"
         source ~/.bash_profile && cd $remoteProjectDIR
         # stop java-tron
     #sh $remoteProjectDIR/stop.sh
     $commands
     exit
 # 这个EOF必须在这个位置，左边不要有空格或缩进
EOF
    }&
    done
    wait
}

checkFirstSend() {
  # 首次构建，推送配置文件、数据
  if [[ ! -f "$deployConfigDir/first.lock" ]]; then
      echo "[info] first send node"
      touch $deployConfigDir/first.lock
      isOverrideConfig=true
      isSendNewDatabase=true
  fi
}


getNodeInfo() {
  echo "get node info"
}

help() {
  echo "help!"
}



restartFn() {
  local nodes=("$@")
  for node in "${nodes[@]}"; do
    {
    echo "[info]: restart node: $node"
    backup_logname=$(date +%Y%m%d%H%M%S)"_backup.log"
    #ssh -p 22008 java-tron@$node "mv $remoteProjectDIR/logs/tron.log $remoteProjectDIR/logs/$backup_logname"
    echo 'backup log name: ' "$backup_logname"
    ssh -p 22008 -Tq java-tron@"$node"<<"EOF"
        source ~/.bash_profile && cd $remoteProjectDIR
        # stop java-tron
    sh $remoteProjectDIR/stop.sh
    sleep 4
    # backup log
    mv $remoteProjectDIR/logs/tron.log $remoteProjectDIR/logs/$backup_logname
        # 复制数据库
        echo "[info] remote $node output-directory"
        rm -rf $remoteProjectDIR/output-directory/
        echo "[info] copy $node output-directory"
        cp -r $remoteProjectDIR/liteDatabase/output-directory/ $remoteProjectDIR
        # restart java-tron
        sh $remoteProjectDIR/start.sh
        find $remoteProjectDIR/logs/ -mtime +10 -name "*" -exec rm -rf {} \;
    exit
# 这个EOF必须在这个位置，左边不要有空格或缩进
EOF
   }&
   done
   wait
}

initParam() {
  echo "[info] init param"

  if [[ ! -d "$workspace" ]]; then
      echo "[info] create workspace:" $workspace
      mkdir -p $workspace
  fi

   # load config
   if [[ -f "$workspace$config.conf" ]]; then
     echo "load config: $workspace$config.conf"
     source $localConfigDir/config.conf
   fi

  # load config
  while [ -n "$1" ]; do
     case "$1" in
     --init)
       isInit=true
       shift 1
       ;;
     --cloneCode)
       isCloneCode=$2
       shift 2
       ;;
     -cc)
       isCloneCode=$2
       shift 2
       ;;
     --repo)
        repositoryUrl=$2
        shift 2
        ;;
     --pull)
       isPullCode=true
       shift 1
       ;;
     -sw)
       isSendWitness=true
       isSendFullNode=false
       isSendAll=false
       shift 1
       ;;
     --sendWitness)
       isSendWitness=true
       isSendFullNode=false
       isSendAll=false
       shift 1
       ;;
     --sendFullNode)
       isSendFullNode=true
       isSendWitness=false
       isSendAll=false
       shift 1
       ;;
     -sf)
       isSendFullNode=true
       isSendWitness=false
       isSendAll=false
       shift 1
       ;;
     --sendAll)
       isSendAll=true
       isSendWitness=false
       isSendFullNode=false
       shift 1
       ;;
     --branch)
       branch=$2
       shift 2
       ;;
     --buildCode)
       isBuildCode=true
       shift 1
       ;;
     -bp)
       isBuildCode=true
       shift 1
       ;;
     --overrideConfig)
       isOverrideConfig=true
       shift 1
       ;;
     -oc)
       isOverrideConfig=true
       shift 1
       ;;
     --useLocal)
       isUseLocalDatabase=true
       shift 1
       ;;
     --sendData)
       isSendNewDatabase=true
       isSendAll=false
       shift 1
       ;;
     --restartSR)
       isSendWitness=false
       isSendFullNode=false
       isRestartWitness=true
       isRestartFullNode=false
       shift 1
       ;;
     --restartFullNode)
       isSendWitness=false
       isSendFullNode=false
       isRestartWitness=false
       isRestartFullNode=true
       shift 1
       ;;
     --restartAll)
       isSendWitness=false
       isSendFullNode=false
       isRestartAll=true
       shift 1
       ;;
     --start)
       isStart=true
       controlNodes=$2
       isSendWitness=false
       isSendFullNode=false
       isRestartAll=false
       if [ -z "$2" ];then
         shift 1
       else
         shift 2
       fi
       ;;
     --stop)
       isStop=true
       controlNodes=$2
       isSendWitness=false
       isSendFullNode=false
       isRestartAll=false
       if [ -z "$2" ];then
         shift 1
       else
         shift 2
       fi
       ;;
     --help)
       shift 1
       ;;
     -h)
       shift 1
       ;;
     *)
       echo "arg: $1 is not a valid parameter"
       exit
       ;;
     esac
  done

}



run() {
  if [[ "$isInit" = true ]]; then
    echo '[info] init'
    initTool
  fi

  # 克隆代码
  if [[ "$isCloneCode" = true ]]; then
    echo '[info] clone code'
    cloneCode
  fi

  # 拉取代码
  if [[ "$isPullCode" = true ]]; then
    echo '[info] pull code'
    pullCode
  fi

  # 编译 jar 包
  if [[ "$isBuildCode" = true ]]; then
    echo '[info] build java-tron'
    buildCode
  fi

  # 发送 SR 节点
  if [[ "$isSendWitness" = true ]]; then
    echo "[info] send sr node!"
    checkFirstSend
    sendWitnessNode
  fi

  # 构建FullNode
  if [[ "$isSendFullNode" = true ]]; then
    echo "[info] send full node!"
    checkFirstSend
    sendFullNode
  fi

  # 发送 SR and FullNode 节点
  if [[ "$isSendAll" = true ]]; then
    checkFirstSend
    sendWitnessNode
    sendFullNode
  fi

  # 启动节点
  if [[ "$isStart" = true ]]; then
    echo "[info] start nodes"
    if [ -z "$controlNodes" ];then
      controlNodes=("${witnessNet[@]}" "${fullnodeNet[@]}")
    fi
    controlType='start'
    nodeControl "${controlNodes[@]}"
  fi

  # 停止节点
  if [[ "$isStop" = true ]]; then
    echo "[info] stop nodes"
    if [ -z "$controlNodes" ];then
      controlNodes=("${witnessNet[@]}" "${fullnodeNet[@]}")
    fi
    controlType='stop'
    nodeControl "${controlNodes[@]}"
  fi

  # 重启远程SR节点
  #   1.停服
  #   2.清库、复制数据库
  #   3.重启
  if [[ "$isRestartWitness" = true ]]; then
    echo "[info] restart witness!"
    restartFn "${witnessNet[@]}"
  fi

  # 重启远程FullNode节点
  #   1.停服
  #   2.清库、复制数据库
  #   3.重启
  if [[ "$isRestartFullNode" = true ]]; then
    echo "[info] restart FullNode"
    restartFn "${fullnodeNet[@]}"
  fi

  # 重启SR、FullNode
  if [[ "$isRestartAll" = true ]]; then
    echo "[info] restart all!"
    allNode=("${witnessNet[@]} ${fullnodeNet[@]}")
    restartFn "${allNode[@]}"
  fi
}

tron() {
  echo "[info] param:" "${@}"
  initParam "${@}"
  run "${@}"
}

if [ "${BASH_SOURCE[0]:-}" != "${0}" ]; then
  export -f tron
else
  tron "${@}"
  exit $?
fi
