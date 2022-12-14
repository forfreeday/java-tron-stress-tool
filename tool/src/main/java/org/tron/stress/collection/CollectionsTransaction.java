package org.tron.stress.collection;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.tron.api.GrpcAPI;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * CollectionsTransaction
 *  Collection of designated network transactions
 * @author liukai
 * @since 2022/9/8.
 */
@Slf4j
public class CollectionsTransaction {

  public static AtomicInteger transferContractCount = new AtomicInteger();
  public static AtomicInteger triggerSmartContractCount = new AtomicInteger();
  public static AtomicInteger transferAssetContractCount = new AtomicInteger();
  private static final String defaultFileName = "transaction.txt";

  // https://developers.tron.network/docs/trongrid
   public static String fullNode = "grpc.trongrid.io:50051";
//  public static String fullNode = "47.252.35.194:50051";
  public static String solidityNode = "grpc.trongrid.io:50052";

  public static String fileName = null;
  public static int startBlockNum = 43421000;
  public static int endBlockNum = startBlockNum + 100;

  public static void main(String[] args) {
    init();
    GrpcClient client = initGRPC(fullNode, solidityNode);
    fetchTransaction(client, fileName, startBlockNum, endBlockNum);
  }

  public static void start() {
    init();
    GrpcClient client = initGRPC(fullNode, solidityNode);
    fetchTransaction(client, fileName, startBlockNum, endBlockNum);
  }


  public static void init() {

    String startBlock = System.getProperty("startBlockNum");
    if (StringUtils.isNoneEmpty(startBlock)) {
      startBlockNum = Integer.parseInt(startBlock);
    }

    String endBlock = System.getProperty("startBlockNum");
    if (StringUtils.isNoneEmpty(endBlock)) {
      endBlockNum = Integer.parseInt(endBlock);
    }

    String file = System.getProperty("fileName");
//    for test
    file = "/Users/liukai/workspaces/temp/fullnode/transaction.txt";
    if (StringUtils.isNoneEmpty(file)) {
      if (!file.contains(".")) {
        file = File.separator + defaultFileName;
      }
      fileName = file;
    } else {
      throw new IllegalArgumentException("file name is null, need to specify file path");
    }

    String fullNode = System.getProperty("fullNode");
    if (StringUtils.isNoneEmpty(fullNode)) {
      CollectionsTransaction.fullNode = fullNode;
    }

  }

  private static void fetchTransaction(GrpcClient client, String fileName, int startBlockNum, int endBlockNum) {
    List<Transaction> transactions = new ArrayList<>(10000000);
    int step = 10;
    int count = 0;
    int filterCount = 0;

    try (FileOutputStream fileOutputStream = new FileOutputStream(new File(fileName), true);
         OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream);
         BufferedWriter writer = new BufferedWriter(outputStreamWriter)) {
      for (int i = startBlockNum; i < endBlockNum; i = i + step) {
        Optional<GrpcAPI.BlockList> result = client.getBlockByLimitNext(i, i + step);
//        Protocol.Block block = client.getBlock(43421000);
        if (!result.isPresent()) {
          continue;
        }
        // ????????????????????????
        collectionTransaction(result.get(), transactions);
        // ????????????
        // ??? 100000 ???????????????????????? List ??????
        if (transactions.size() >= 100000) {
          count = count + transactions.size();
          transactions = filterTransaction(transactions);
          filterCount = filterCount + transactions.size();
          writeToFile(writer, fileName, transactions);
        }
        logger.info("???????????????{} -- {} ????????????!, ??????: {}", i, i + step, transactions.size());
      }

      if (transactions.size() < 100000) {
        count = count + transactions.size();
        transactions = filterTransaction(transactions);
        count = count + transactions.size();
        writeToFile(writer, fileName, transactions);
        logger.info("???????????????????????????{}, ????????????????????????: {}", count, filterCount);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void collectionTransaction(GrpcAPI.BlockList blockList, List<Transaction> transactions) {
    if (blockList.getBlockCount() > 0) {
      for (Protocol.Block block : blockList.getBlockList()) {
        if (block.getTransactionsCount() > 0) {
          transactions.addAll(block.getTransactionsList());
        }
      }
    }
  }

  private static GrpcClient initGRPC(String fullNode, String solidityNode) {
    return new GrpcClient(fullNode, solidityNode);
  }

  private static List<Transaction> filterTransaction(List<Transaction> transactions) {
    return transactions
            .stream()
            .filter(transaction -> {
              Transaction.Contract.ContractType type = transaction.getRawData().getContract(0).getType();

              if (type == Transaction.Contract.ContractType.TransferContract) {
                transferContractCount.incrementAndGet();
                return true;
              }
              if (type == Transaction.Contract.ContractType.TriggerSmartContract) {
                triggerSmartContractCount.incrementAndGet();
                return true;
              }
              if (type == Transaction.Contract.ContractType.TransferAssetContract) {
                transferAssetContractCount.incrementAndGet();
                return true;
              }
              if (type == Transaction.Contract.ContractType.FreezeBalanceContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.UnfreezeBalanceContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.ExchangeCreateContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.ExchangeInjectContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.ExchangeWithdrawContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.ExchangeTransactionContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.CreateSmartContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.ParticipateAssetIssueContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.AccountCreateContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.VoteWitnessContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.AccountPermissionUpdateContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.MarketSellAssetContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.MarketCancelOrderContract) {
                return true;
              }
              if (type == Transaction.Contract.ContractType.WithdrawBalanceContract) {
                return true;
              }

              return type == Transaction.Contract.ContractType.TransferContract
                      || type == Transaction.Contract.ContractType.TransferAssetContract
                      || type == Transaction.Contract.ContractType.AccountCreateContract
                      || type == Transaction.Contract.ContractType.VoteAssetContract
                      || type == Transaction.Contract.ContractType.AssetIssueContract
                      || type == Transaction.Contract.ContractType.ParticipateAssetIssueContract
                      || type == Transaction.Contract.ContractType.FreezeBalanceContract
                      || type == Transaction.Contract.ContractType.UnfreezeBalanceContract
                      || type == Transaction.Contract.ContractType.UnfreezeAssetContract
                      || type == Transaction.Contract.ContractType.UpdateAssetContract
                      || type == Transaction.Contract.ContractType.ProposalCreateContract
                      || type == Transaction.Contract.ContractType.ProposalApproveContract
                      || type == Transaction.Contract.ContractType.ProposalDeleteContract
                      || type == Transaction.Contract.ContractType.SetAccountIdContract
                      || type == Transaction.Contract.ContractType.CustomContract
                      || type == Transaction.Contract.ContractType.CreateSmartContract
                      || type == Transaction.Contract.ContractType.TriggerSmartContract
                      || type == Transaction.Contract.ContractType.ExchangeCreateContract
                      || type == Transaction.Contract.ContractType.UpdateSettingContract
                      || type == Transaction.Contract.ContractType.ExchangeInjectContract
                      || type == Transaction.Contract.ContractType.ExchangeWithdrawContract
                      || type == Transaction.Contract.ContractType.ExchangeTransactionContract
                      || type == Transaction.Contract.ContractType.UpdateEnergyLimitContract
                      || type == Transaction.Contract.ContractType.AccountUpdateContract
                      || type == Transaction.Contract.ContractType.WithdrawBalanceContract
                      || type == Transaction.Contract.ContractType.AccountCreateContract
                      || type == Transaction.Contract.ContractType.VoteWitnessContract
                      || type == Transaction.Contract.ContractType.AccountPermissionUpdateContract
                      || type == Transaction.Contract.ContractType.MarketSellAssetContract
                      || type == Transaction.Contract.ContractType.MarketCancelOrderContract
                      ;

            }).collect(Collectors.toList());
  }

  public static String transactionToHexString(Transaction trx) {
    return Hex.toHexString(trx.toByteArray());
  }

  private static void writeToFile(BufferedWriter writer, String fileName, List<Transaction> transactions) {
    long startTime = System.currentTimeMillis();
    logger.info("???????????????1??????????????????????????????...");
    transactions.parallelStream().forEachOrdered(trx -> {
      try {
        writer.write(transactionToHexString(trx) + System.lineSeparator());
        writer.flush();
      } catch (IOException e) {
        e.printStackTrace();
      }
    });
    logger.info("????????????????????????????????????????????????" + fileName);
    logger.info("??????????????????" + (System.currentTimeMillis() - startTime) + "ms");
    transactions.clear();
  }

}
