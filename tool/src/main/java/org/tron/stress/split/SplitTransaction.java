package org.tron.stress.split;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.tron.common.utils.ByteArray;
import org.tron.protos.Protocol.Transaction;
import org.tron.protos.contract.SmartContractOuterClass;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SplitTransaction
 *
 * @author liukai
 * @since 2022/10/26.
 */
@Slf4j
public class SplitTransaction {

  private static String SPLIT_DIR = "/data/workspace/replay_workspace/data/split/";
  private static String TRANSACTION_DIR = null;
  private static String TRANSACTION_NAME = null;

  private static AtomicInteger transferCount = new AtomicInteger(0);
  private static AtomicInteger trc10Count = new AtomicInteger(0);
  private static AtomicInteger trc20Count = new AtomicInteger(0);

  private static String PREFIX = "split_result";
  private static String SUFFIX = ".txt";

  private static String TRX_TYPE = null;

  public static void init() {
    String trxTypeParam = System.getProperty("trxType");
    if (StringUtils.isNoneEmpty(trxTypeParam)) {
      TRX_TYPE = trxTypeParam;
    }

    String splitDirParam = System.getProperty("splitDir");
    if (StringUtils.isNoneEmpty(splitDirParam)) {
      SPLIT_DIR = splitDirParam;
    }

    String trxDirParam = System.getProperty("trxDir");
    if (StringUtils.isNoneEmpty(trxDirParam)) {
      TRANSACTION_DIR = trxDirParam;
    } else {
      throw new IllegalArgumentException("TRANSACTION_DIR cannot be null");
    }

    String trxFileNameParam = System.getProperty("trxFileName");
    if (StringUtils.isNoneEmpty(trxFileNameParam)) {
      TRANSACTION_NAME = trxFileNameParam;
    } else {
      TRANSACTION_NAME = "transactions.txt";
    }
    logger.info("init params, trxType: {}, splitDir: {}, trxDir: {}, trxFileName: {}", TRX_TYPE, SPLIT_DIR, TRANSACTION_DIR, TRANSACTION_NAME);
  }

  public static void split() throws IOException {
    File transactionSource = new File(TRANSACTION_DIR + TRANSACTION_NAME);
    FileWriter splitTransfer = new FileWriter(SPLIT_DIR + "trx_transfer.txt");
    FileWriter splitTRC10 = new FileWriter(SPLIT_DIR + "token10_transfer.txt");
    FileWriter splitTRC20 = new FileWriter(SPLIT_DIR + "usdt_transfer.txt");
    int count = 0;
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(transactionSource)))) {
      String line = reader.readLine();
      while (line != null) {
        Transaction tx = Transaction.parseFrom(Hex.decode(line));
        Transaction.Contract.ContractType contractType = tx.getRawData().getContract(0).getType();
        if (TRX_TYPE != null && !contractType.getValueDescriptor().getName().equals(TRX_TYPE)) {
          continue;
        }
        switch (contractType) {
          case TransferContract:
            splitTransfer.write(line + "\n");
            count(transferCount, "TransferContract");
            if (count % 10000 == 0) {
              logger.info("trx type: TransferContract, flush, count: {}", count);
              flush(splitTransfer);
            }
            break;
          case TransferAssetContract:
            splitTRC10.write(line + "\n");
            count(trc10Count, "TransferAssetContract");
            if (count % 10000 == 0) {
              logger.info("trx type: TransferAssetContract, flush, count: {}", count);
              flush(splitTRC10);
            }
            break;
          case TriggerSmartContract:
            SmartContractOuterClass.TriggerSmartContract triggerSmartContract = tx.getRawData().getContract(0).getParameter()
                    .unpack(SmartContractOuterClass.TriggerSmartContract.class);
            byte[] contractAddressBytes = triggerSmartContract.getContractAddress().toByteArray();
            if (ByteArray.toHexString(contractAddressBytes)
                    .equalsIgnoreCase("41A614F803B6FD780986A42C78EC9C7F77E6DED13C")) {
              splitTRC20.write(line + "\n");
            }
            count(trc20Count, "TriggerSmartContract");
            if (count % 10000 == 0) {
              logger.info("trx type: splitTRC20, flush, count: {}", count);
              flush(splitTRC20);
            }
            break;
          default:
            break;
        }
        count++ ;
        if (count % 10000 == 0) {
          logger.info("current transaction count: {}", count);
          splitTransfer.flush();
          splitTRC10.flush();
          splitTRC20.flush();
        }
        line = reader.readLine();
      }
      splitTransfer.flush();
      splitTRC10.flush();
      splitTRC20.flush();
    } catch (Exception e) {
      logger.error("split exception, count: {}, message: {}", count, e.getMessage(), e);
    }
  }



  public static void flush(FileWriter fileWriter) {
    try {
      fileWriter.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void count (AtomicInteger count, String trxType) {
    logger.info("transaction type: {}, current count: {}", trxType, count.incrementAndGet());
  }

  public static void start() {
    try {
      init();
      split();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
