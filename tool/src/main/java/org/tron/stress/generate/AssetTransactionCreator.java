package org.tron.stress.generate;

import com.google.protobuf.ByteString;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.crypto.ECKey;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Commons;
import org.tron.stress.generate.GenerateTransaction;
import org.tron.stress.design.factory.Creator;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;
import org.tron.protos.contract.AssetIssueContractOuterClass;

/**
 * @author liukai
 * @since 2022/9/9.
 */
@Setter
@Creator(type = "asset")
@Slf4j
public class AssetTransactionCreator extends org.tron.stress.generate.AbstractTransactionCreator implements org.tron.stress.generate.TransactionCreator {

  private String assetName = "1002136";
  private static String ownerAddress = "TXtrbmfwZ2LxtoCveEhZT86fTss1w8rwJE";
  private static String privateKey = "0528dc17428585fc4dece68b79fa7912270a1fe8e85f244372f59eb7e8925e04";
  private long amount = 1L;

  @Override
  public Protocol.Transaction create() {
    String curAccount = GenerateTransaction.accountQueue.poll();
    AssetIssueContractOuterClass.TransferAssetContract contract = AssetIssueContractOuterClass.TransferAssetContract.newBuilder()
            .setAssetName(ByteString.copyFrom(assetName.getBytes()))
            .setOwnerAddress(ByteString.copyFrom(Commons.decodeFromBase58Check(ownerAddress)))
            .setToAddress(ByteString.copyFrom(Commons.decodeFromBase58Check(curAccount)))
            .setAmount(amount)
            .build();
    Protocol.Transaction transaction = createTransaction(contract, ContractType.TransferAssetContract);
    GenerateTransaction.accountQueue.offer(curAccount);
    return sign(transaction, ECKey.fromPrivate(ByteArray.fromHexString(privateKey)));
  }

}
