#!/bin/bash

fullNodes='10.40.100.118:50051;10.40.100.117:50051'
thread=4
trxFilePath=''
qps=100
# 最大发送条数
maxRows=-1
# unit seconds
maxTime=3600
# transaction type
type=sendTx

startNum=0
endNum=2000000

nohup java -Xms5G -Xmx5G -XX:ReservedCodeCacheSize=256m -XX:MetaspaceSize=256m \
 -XX:MaxMetaspaceSize=512m -XX:MaxDirectMemorySize=1G  \
 -XX:+UseConcMarkSweepGC -XX:NewRatio=2 -XX:+CMSScavengeBeforeRemark -XX:+ParallelRefProcEnabled \
 -XX:+HeapDumpOnOutOfMemoryError -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=70 \
 -Dtype=$type -DfullNodes=$fullNodes -Dthread=$thread -DfilePath=$trxFilePath -Dqps=$qps -DmaxRows=$maxRows -DmaxTime=$maxTime -jar Stress.jar >> sendtx.log 2>&1 &
