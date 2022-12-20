#!/bin/bash

# transaction type
type=broadcast
#-------------------
configPath=''

nohup java -Xms5G -Xmx5G -XX:ReservedCodeCacheSize=256m -XX:MetaspaceSize=256m \
  -XX:MaxMetaspaceSize=512m -XX:MaxDirectMemorySize=1G \
  -XX:+UseConcMarkSweepGC -XX:NewRatio=2 -XX:+CMSScavengeBeforeRemark -XX:+ParallelRefProcEnabled \
  -XX:+HeapDumpOnOutOfMemoryError -XX:+UseCMSInitiatingOccupancyOnly -XX:CMSInitiatingOccupancyFraction=70 \
  -Dtype=$type \
  -jar Stress.jar -c $configPath >>sendtx.log 2>&1 &
