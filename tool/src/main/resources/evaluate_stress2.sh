#!/usr/bin/env bash

if [ $# != 1 ] && [ $# != 3 ]; then
  echo "usage: sh $0 <logfile_path> [<start_time> <end_time>]"
  exit
fi

if [ ! -f $1 ]; then
  echo "file $1 not exist!"
  exit
fi

if [ $# = 1 ]; then
  # grep -n "txs size=" $1   过滤所有交易得到
  #  行号  :trx size
  # 464669:txs size=820
  # 465256:txs size=604
  # 465779:txs size=718
  # 466244:txs size=106
  grep -n "txs size=" $1 | awk -F":"  '{split($2,a,"=");if(a[2]>=1000){print $1}}' >tmp123.txt

  first_line=$(head -1 tmp123.txt)
  last_line=$(tail -1 tmp123.txt)
  rm -rf tmp123.txt
  let "first_line=$first_line-10"
  # ? +2行的意义？
  let "last_line=$last_line+2"
fi

if [ $# = 3 ]; then
  awk -v start_time=$2 -v end_time=$3 '{if($1>=start_time && $1<=end_time){print NR}}' $1 >tmp123.txt
  first_line=$(head -1 tmp123.txt)
  last_line=$(tail -1 tmp123.txt)
  rm -rf tmp123.txt
fi

if [ $first_line -gt $last_line ]; then
  echo "invalid first_line $first_line last_line $last_line"
  exit
fi

#--------------------------------
# 生成 xx.log，first_line last_line 之间的完整日志
awk -v first_line=$first_line -v last_line=$last_line '{if(NR>=first_line && NR<=last_line){print}}' $1 >xx.log

first_time=$(head -1 xx.log | awk '{print $1}')
last_time=$(tail -1 xx.log | awk '{print $1}')
echo "test_time: $first_time ~ $last_time"

first_second=$(head -1 xx.log | awk '{print substr($1,0,2)*3600 + substr($1,4,2)* 60 + substr($1,7,2)}')
last_second=$(tail -1 xx.log | awk '{print substr($1,0,2)*3600 + substr($1,4,2)* 60 + substr($1,7,2)}')
# time_delta 有问题！！！！
let "time_delta=$last_second-$first_second-3"
echo "total_time_cost(seconds):" $time_delta

maxPending=$(grep "Pending tx size:" xx.log | awk -F":" '{print $5}' | sort -k 1nr | head -1)
echo "maxPendingTxSize:" ${maxPending}

#there may be some same block, only add once, so uniq for continue same
# 这里有问题，不止是 txs size 空块是 txs are empty
totalTxNum=$(grep "txs size=" xx.log | uniq | awk -F"=" '{sum+=$2}END{print sum}')
echo "packetTxNum:" ${totalTxNum}

if [ ! -n "$totalTxNum" ]; then
  echo "ERROR: no transaction exist!"
  exit
fi

fork_num=$(grep "switch fork" xx.log | wc -l)
echo "fork_num:" $fork_num

grep "PushBlock block" xx.log | awk -F"/" 'BEGIN{max=0}{a=substr($2,5,4);count+=1;total+=a;if(a+0>max){max=a+0}}END{print total/count,max}' >tmp123.txt
avg_push_time=$(awk '{print $1}' tmp123.txt)
max_push_time=$(awk '{print $2}' tmp123.txt)
rm -rf tmp123.txt
echo "avg_pushBlock_time (ms):" $avg_push_time
echo "max_pushBlock_time (ms):" $max_push_time

let "tps=$totalTxNum/$time_delta"
echo "avg_tps:" $tps

maxTxSize=$(grep "txs size=" xx.log | awk -F"=" '{print $2}' | sort -k 1nr | head -1)
let "max_tps=$maxTxSize/3"
echo "max_tps:" $max_tps

min_block_num=$(grep "number=" xx.log | awk -F"=" '{print $2}' | head -1)
max_block_num=$(grep "number=" xx.log | awk -F"=" '{print $2}' | tail -1)
# 计算维护期
#Do update nextMaintenanceTime
maintenanceCount=`grep 'Do update nextMaintenanceTime' $1 |wc -l`

# not equal to grep "number=" xx.log | wc -l
let "generate_block_num=$max_block_num-$min_block_num+1"

#let generate_block_num=`grep 'generated by myself' xx.log |wc -l`
echo "generate_block_num:" $generate_block_num
echo "min_block_num": $min_block_num
echo "max_block_num": $max_block_num
# time_delta 结束时间 - 开始时间
# $time_delta/3 应该产的块数
let "generate_block_theory=$time_delta/3-$maintenanceCount" #some times not accurate

if [ $generate_block_num -gt $generate_block_theory ]; then
  let "generate_block_theory=$generate_block_num"
fi

echo "generate_block_theory:" $generate_block_theory
missing_block_rate=$(echo $generate_block_theory $generate_block_num | awk '{ printf "%0.2f%%", ($1-$2)*100/$1}')
echo "maintenance:" $maintenanceCount
echo "missing_block_rate:" $missing_block_rate

alarm_data="test_time: $first_time ~ $last_time
total_time_cost(seconds): $time_delta
maxPendingTxSize: ${maxPending}
packetTxNum: ${totalTxNum}
fork_num: $fork_num
avg_pushBlock_time (ms): $avg_push_time
max_pushBlock_time (ms): $max_push_time
avg_tps: $tps
max_tps: $max_tps
maintenance: $maintenanceCount
generate_block_num: $generate_block_num
generate_block_theory: $generate_block_theory
missing_block_rate: $missing_block_rate
"

json="{\"text\":\"\`\`\`$alarm_data\`\`\`\"}"
#echo $json
#curl -i -X POST -H "'Content-type':'application/json'" -d "$json" https://hooks.slack.com/services/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
