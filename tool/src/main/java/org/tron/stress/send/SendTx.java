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
import java.util.ArrayList;
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
  ScheduledExecutorService scheduledExecutorService;
  private List<WalletGrpc.WalletBlockingStub> blockingStubFullList = new ArrayList<>();
  private int onceSendTxNum; //batch send num once
  private int maxRows; //max read rows from file
  private static boolean isScheduled = false;
  private static String[] fullNodes;
  private static int broadcastThreadNum;
  private static int batch;
  private static int rows;
  private static String filePath;

  public SendTx(String[] fullNodes, int broadcastThreadNum, int onceSendTxNum, int maxRows) {
    broadcastExecutorService = Executors.newFixedThreadPool(broadcastThreadNum);
    scheduledExecutorService = Executors.newScheduledThreadPool(broadcastThreadNum);
    for (String fullNode : fullNodes) {
      //construct grpc stub
      ManagedChannel channelFull = ManagedChannelBuilder.forTarget(fullNode)
          .usePlaintext(true).build();
      WalletGrpc.WalletBlockingStub blockingStubFull = WalletGrpc.newBlockingStub(channelFull);
      blockingStubFullList.add(blockingStubFull);
      this.onceSendTxNum = onceSendTxNum;
      this.maxRows = maxRows;
    }
  }

  private void sendTxByScheduled(List<Transaction> list) {
    Random random = new Random();
    list.forEach(transaction -> {
      scheduledExecutorService.schedule(()-> {
        int index = random.nextInt(blockingStubFullList.size());
        blockingStubFullList.get(index).broadcastTransaction(transaction);
      },1, TimeUnit.SECONDS);
    });
  }

  private void send(List<Transaction> list){
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
    File file = new File(path);
    logger.info("[Begin] send tx");
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(new FileInputStream(file)))) {
      String line = reader.readLine();
      List<Transaction> lineList = new ArrayList<>();
      int count = 0;
      while (line != null) {
        try {
          lineList.add(Transaction.parseFrom(Hex.decode(line)));
          count += 1;
          if (count > maxRows) {
            break;
          }
          if (count % onceSendTxNum == 0) {
            sendTx(lineList);
            lineList.clear();
            logger.info("Send tx num = " + count);
          }
        } catch (Exception e) {
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
    broadcastThreadNum = 1;
    if (StringUtils.isNoneEmpty(broadcastThreadParam)) {
      broadcastThreadNum = Integer.parseInt(broadcastThreadParam);
    }

    String filePathParam = System.getProperty("filePath");
    filePath = null;
    if (StringUtils.isNoneEmpty(filePathParam)) {
      filePath = filePathParam;
    }

    String batchParam = System.getProperty("batch");
    batch = 0;
    if (StringUtils.isNoneEmpty(batchParam)) {
      batch = Integer.parseInt(batchParam);
    }

    String maxRowsParam = System.getProperty("maxRows");
    rows = -1;
    if (StringUtils.isNoneEmpty(maxRowsParam)) {
      rows = Integer.parseInt(maxRowsParam);
    }

    String scheduledParam = System.getProperty("scheduled");
    if (StringUtils.isNoneEmpty(scheduledParam)) {
      isScheduled = true;
    }

//    if (args.length > 4) {
//      maxRows = Integer.parseInt(args[4]);
//    }
    if (rows < 0) {
      rows = Integer.MAX_VALUE;
    }
    if (batch > rows) {
      System.out.println("maxRows must >= onceSendTxNum !");
      System.exit(0);
    }
  }

  public static void start() {
    init();
    SendTx sendTx = new SendTx(fullNodes, broadcastThreadNum, batch, rows);

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
}
