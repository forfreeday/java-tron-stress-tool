package org.tron.stress.send;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.tron.common.application.Application;
import org.tron.common.application.ApplicationFactory;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.parameter.CommonParameter;
import org.tron.core.Constant;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.net.message.TransactionMessage;
import org.tron.core.services.RpcApiService;
import org.tron.core.services.http.FullNodeHttpApiService;
import org.tron.protos.Protocol;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * @author liukai
 * @since 2022/12/19.
 */
@Slf4j
public class RunBroadcast {
  private static Integer startNum = 0;
  private static Integer endNum = null;
  private static int maxRows;
  private static int maxTime;

  @Resource
  private Broadcast broadcast;

  public static void main(String[] args) {
    logger.info("Full node running.");
    Args.setParam(args, Constant.TESTNET_CONF);
    CommonParameter parameter = Args.getInstance();

    if (parameter.isHelp()) {
      logger.info("Here is the help message.");
      return;
    }

    if (Args.getInstance().isDebug()) {
      logger.info("in debug mode, it won't check energy time");
    } else {
      logger.info("not in debug mode, it will check energy time");
    }

    // 启动 Spring 容器
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.setAllowCircularReferences(false);
    TronApplicationContext context =
            new TronApplicationContext(beanFactory);
    context.register(DefaultConfig.class);
    context.refresh();

    Application appT = ApplicationFactory.create(context);
    shutdown(appT);

    // grpc api server
    RpcApiService rpcApiService = context.getBean(RpcApiService.class);
    appT.addService(rpcApiService);

    // http api server
    FullNodeHttpApiService httpApiService = context.getBean(FullNodeHttpApiService.class);

    appT.addService(httpApiService);
    appT.initServices(parameter);
    appT.startServices();
    appT.startup();

    // 初始化测试相关服务
    Broadcast bean = context.getBean(Broadcast.class);
    bean.init();
    String path = "/data/test/";
    new RunBroadcast().readTxAndSend(path);
  }

  private void readTxAndSend(String path) {
    logger.info("[Begin] send tx");
    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new FileInputStream(new File(path))))) {
      LocalDateTime lastTime = LocalDateTime.now().plus(maxTime, ChronoUnit.SECONDS);
      int count = 0;
      int currentLineNumber=0;
      String line = reader.readLine();
      while (line != null) {
        if (startNum != null && startNum > 0) {
          if (currentLineNumber <= startNum) {
            // skip line
            line = reader.readLine();
            currentLineNumber++;
            logger.info("skip transaction, startNum:{}, concurrent num: {}", startNum, currentLineNumber);
            continue;
          }
        }
        if (endNum != null && endNum > 0) {
          if (currentLineNumber >= endNum) {
            logger.info("end of reading, endNum: {}", endNum);
            System.exit(0);
          }
        }
        count += 1;
        if (count > maxRows) {
          logger.info("maximum number of sends reached, exit execution, maxRows: {}", maxRows);
          break;
        }
        line = reader.readLine();
        Protocol.Transaction transaction = Protocol.Transaction.parseFrom(Hex.decode(line));
        broadcast.broadcast(new TransactionMessage(transaction));
        currentLineNumber++;
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    logger.info("[Final] send tx end");
  }

  private static long duration(LocalDateTime now, LocalDateTime lastTime) {
    Duration duration = Duration.between(now, lastTime);
    return duration.toMillis() / 1000;
  }

  public static void shutdown(final Application app) {
    logger.info("********register application shutdown hook********");
    Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
  }
}
