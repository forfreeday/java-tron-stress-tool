package org.tron.stress;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.stress.collection.CollectionsTransaction;
import org.tron.stress.generate.GenerateTransaction;
import org.tron.stress.send.SendTx;
import org.tron.stress.split.SplitTransaction;

/**
 * Stress entrance
 *
 * @author liukai
 * @since 2022/10/26.
 */
@Slf4j
public class Stress {

  public static void main(String[] args) {
    String typeParam = System.getProperty("type");
    logger.info("business type: {}", typeParam);
    if (StringUtils.isNoneEmpty(typeParam)) {
      if (typeParam.equals("collection")) {
        CollectionsTransaction.start();
      } else if (typeParam.equals("generate")) {
        GenerateTransaction.start();
      } else if (typeParam.equals("sendTx")) {
        new SendTx().start();
      } else if (typeParam.equals("split")) {
        SplitTransaction.start();
      } else if (typeParam.equals("broadcast")) {
        SplitTransaction.start();
      } else if (typeParam.equals("init")) {

      }
    }
  }
}
