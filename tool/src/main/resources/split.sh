#!/bin/bash
TRX_DIR=/data/
TRX_NAME=getTransactions.txt
TRX_TYPE=null
type=sendTx

SPLIT_DIR=/data/result/

if [ ! -d $SPLIT_DIR ]; then
  mkdir -p $SPLIT_DIR
fi

java -Xms5G -Xmx5G -XX:ReservedCodeCacheSize=256m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m \
-XX:MaxDirectMemorySize=1G  -XX:+UseConcMarkSweepGC -XX:NewRatio=2 \
-XX:+CMSScavengeBeforeRemark -XX:+ParallelRefProcEnabled -XX:+HeapDumpOnOutOfMemoryError -XX:+UseCMSInitiatingOccupancyOnly \
-XX:CMSInitiatingOccupancyFraction=70 -Dtype=split -DsplitDir=$SPLIT_DIR -DtrxDir=$TRX_DIR -DtrxFileName=$TRX_NAME \
-DtrxType=$TRX_TYPE -jar Stress.jar
