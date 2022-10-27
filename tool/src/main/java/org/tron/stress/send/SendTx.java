package org.tron.stress.send;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.tron.api.WalletGrpc;
import org.tron.protos.Protocol.Transaction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SendTx {

  private ExecutorService broadcastExecutorService;
  private ScheduledExecutorService scheduledExecutorService;
  private List<WalletGrpc.WalletBlockingStub> blockingStubFullList = new ArrayList<>();
  private static int batchNum = 100; //batch send num once
  private static int maxRows; //max read rows from file
  private static boolean isScheduled = false;
  private static String[] fullNodes;
  private static int broadcastThreadNum = 1;
  private static String filePath;
  private static int maxTime;

  public SendTx(String[] fullNodes, int broadcastThreadNum) {
    initExecutors(broadcastThreadNum);
    initGRPC(fullNodes);
  }

  public void initExecutors(int broadcastThreadNum) {
    broadcastExecutorService = Executors.newFixedThreadPool(broadcastThreadNum);
    scheduledExecutorService = Executors.newScheduledThreadPool(broadcastThreadNum);
  }

  public void initGRPC(String[] fullNodes) {
    for (String fullNode : fullNodes) {
      //construct grpc stub
      logger.info("init grpc fullNode ip: {}", fullNode);
      ManagedChannel channelFull = ManagedChannelBuilder.forTarget(fullNode)
              .usePlaintext(true).build();
      WalletGrpc.WalletBlockingStub blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
      blockingStubFullList.add(blockingStubFull);

    }
  }

  private void sendTxByScheduled(List<Transaction> list) {
    Random random = new Random();
    list.forEach(transaction -> {
      scheduledExecutorService.schedule(() -> {
        int index = random.nextInt(blockingStubFullList.size());
        blockingStubFullList.get(index).broadcastTransaction(transaction);
      }, 1, TimeUnit.SECONDS);
    });
  }

  private void send(List<Transaction> list) {
    Random random = new Random();
    List<Future<Boolean>> futureList = new ArrayList<>(list.size());
    list.forEach(transaction -> {
      futureList.add(broadcastExecutorService.submit(() -> {
        int index = random.nextInt(blockingStubFullList.size());
        blockingStubFullList.get(index).broadcastTransaction(transaction);
        return true;
      }));
    });
    futureList.forEach(ret -> {
      try {
        ret.get();
      } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
      }
    });

  }

  private void sendTx(List<Transaction> list) {
    if (isScheduled) {
      sendTxByScheduled(list);
    } else {
      send(list);
    }
  }

  private void readTxAndSend(String path) {
    logger.info("[Begin] send tx");
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(new File(path))))) {
      LocalDateTime lastTime = LocalDateTime.now().plus(maxTime, ChronoUnit.SECONDS);
      List<Transaction> lineList = new ArrayList<>();
      int count = 0;
      String line = reader.readLine();
      while (line != null) {
        lineList.add(Transaction.parseFrom(Hex.decode(line)));
        count += 1;
        if (count > maxRows) {
          logger.info("maximum number of sends reached, exit execution, maxRows: {}", maxRows);
          break;
        }

        if (duration(LocalDateTime.now(), lastTime) <= 0) {
          logger.info("maximum execution time reached, exit execution, maxTime: {}", maxTime);
          break;
        }
        if (count % batchNum == 0) {
          sendTx(lineList);
          lineList.clear();
          logger.info("Send tx num = " + count);
        }
        line = reader.readLine();
      }
      if (!lineList.isEmpty()) {
        sendTx(lineList);
        lineList.clear();
        logger.info("Send total tx num = " + count);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (null != broadcastExecutorService) {
        broadcastExecutorService.shutdown();
      }
      if (null != scheduledExecutorService) {
        scheduledExecutorService.shutdown();
      }
    }
    logger.info("[Final] send tx end");
  }

  public static void init() {
    String fullNodesParam = System.getProperty("fullNodes");
    if (StringUtils.isNoneEmpty(fullNodesParam)) {
      fullNodes = fullNodesParam.split(";");
    } else {
      fullNodes = new String[]{fullNodesParam};
    }

    String broadcastThreadParam = System.getProperty("thread");
    if (StringUtils.isNoneEmpty(broadcastThreadParam)) {
      broadcastThreadNum = Integer.parseInt(broadcastThreadParam);
    }

    String filePathParam = System.getProperty("filePath");
    if (StringUtils.isNoneEmpty(filePathParam)) {
      filePath = filePathParam;
    }

    String qpsParam = System.getProperty("qps");
    if (StringUtils.isNoneEmpty(qpsParam)) {
      batchNum = Integer.parseInt(qpsParam);
    }
    String maxTimeParam = System.getProperty("maxTime");
    if (StringUtils.isNoneEmpty(maxTimeParam)) {
      maxTime = Integer.parseInt(maxTimeParam);
    }

    String maxRowsParam = System.getProperty("maxRows");
    if (StringUtils.isNoneEmpty(maxRowsParam)) {
      maxRows= Integer.parseInt(maxRowsParam);
      if (maxRows < 0) {
        maxRows = Integer.MAX_VALUE;
      }
    } else {
      maxRows = Integer.MAX_VALUE;
    }

    String scheduledParam = System.getProperty("scheduled");
    if (StringUtils.isNoneEmpty(scheduledParam)) {
      isScheduled = true;
    }

    if (batchNum > maxRows) {
      logger.info("maxRows must >= qps !");
      System.exit(0);
    }
    logger.info("init params: fullNodes: {}, thread: {}, filePath: {}, batchNum: {}, maxTime: {}, maxRows: {}, scheduled: {}",
            Arrays.toString(fullNodes), broadcastThreadNum, filePath, batchNum, maxTime, maxRows, isScheduled);
  }

  public static void start() {
    init();
    SendTx sendTx = new SendTx(fullNodes, broadcastThreadNum);
    int isMultiFile = filePath.indexOf(";");
    if (isMultiFile != -1) {
      String[] filePaths = filePath.split(";");
      for (String path : filePaths) {
        logger.info("fileName: {}", path);
        sendTx.readTxAndSend(path);
      }
    } else {
      sendTx.readTxAndSend(filePath);
    }
  }

  private static long duration(LocalDateTime now, LocalDateTime lastTime) {
    Duration duration = Duration.between(now, lastTime);
    return duration.toMillis() / 1000;
  }

  //for test
  public static void main(String[] args) {
    start();
  }
}
