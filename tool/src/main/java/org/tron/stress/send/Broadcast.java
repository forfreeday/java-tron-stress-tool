package org.tron.stress.send;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.common.overlay.message.Message;
import org.tron.common.overlay.server.SyncPool;
import org.tron.common.utils.Sha256Hash;
import org.tron.core.capsule.BlockCapsule;
import org.tron.core.net.message.BlockMessage;
import org.tron.core.net.message.InventoryMessage;
import org.tron.core.net.message.TransactionMessage;
import org.tron.core.net.peer.PeerConnection;
import org.tron.protos.Protocol.Inventory.InventoryType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author liukai
 * @since 2022/12/15.
 */
@Slf4j
@Component
public class Broadcast {

  private boolean isBroadcast;
  private static int TPS = 100;
  private AtomicLong count = new AtomicLong(0);

  @Autowired
  private SyncPool syncPool;

  // broadcast message 存储待广播的Map
  private ConcurrentHashMap<Sha256Hash, InventoryType> advObjToSpread = new ConcurrentHashMap<>();

  // broadcastPool 广播消息线程池
  private ExecutorService broadcastPool = Executors.newFixedThreadPool(2,
          r -> new Thread(r, "broadcastPool"));

  private Cache<Sha256Hash, TransactionMessage> trxCache = CacheBuilder.newBuilder()
          .maximumSize(100_000_000).expireAfterWrite(1, TimeUnit.HOURS).initialCapacity(100_000_000)
          .recordStats().build();

  private Cache<Sha256Hash, BlockMessage> blockCache = CacheBuilder.newBuilder()
          .maximumSize(10).expireAfterWrite(60, TimeUnit.SECONDS)
          .recordStats().build();

  /**
   * 广播数据
   * 需要在程序启动时，进行初始化
   */
  public void init() {
    logger.info("init broatcast");
    isBroadcast = true;
    checkAndStart();
  }

  public void checkAndStart() {

    // 广播
    broadcastPool.submit(() -> {
      while (isBroadcast) {
        consumerAdvToSpread();
      }
    });
  }

  // 广播由 broadcast 方法构建的交易
  private void consumerAdvToSpread() {
    // for test
    TPS = 3000;
    long starTime = System.currentTimeMillis();
    if (advObjToSpread.isEmpty()) {
      return;
    }

    // 待发送中间队列
    ConcurrentHashMap<Sha256Hash, InventoryType> spread = new ConcurrentHashMap<>();
    AtomicInteger invCount = new AtomicInteger(0);
    synchronized (advObjToSpread) {
      for (Map.Entry<Sha256Hash, InventoryType> inventoryTypeEntry : advObjToSpread.entrySet()) {
        // TPS 控制
        if (invCount.getAndIncrement() >= TPS) {
          break;
        }
        // 把数据导入到待发送队列，这个需要优化，复制次数太多了
        spread.put(inventoryTypeEntry.getKey(), inventoryTypeEntry.getValue());
        // 已准备好的待发送交易移除
        advObjToSpread.remove(inventoryTypeEntry.getKey());
      }
    }

    int n = 0;
    while (advObjToSpread.size() > 0 && syncPool.getActivePeers().size() > 0) {
      logger.info("SPREAD {} advObjToSpread:{} spreadSize: {}", ++n, advObjToSpread.size(), spread.size());

      if(spread.size() <= 0) {
        break;
      }

      InvSender sendPackage = new InvSender();
      // 把数据导从 spread 导到真正要发送的 sendPackage 当中的队列
      spread.entrySet().forEach(id -> {
        syncPool.getActivePeers().stream()
                .filter(peer -> sendPackage.getSize(peer) < 1000)
                .min(Comparator.comparingInt(sendPackage::getSize))
                .ifPresent(peerConnection -> {
                  sendPackage.add(id, peerConnection);
                  spread.remove(id.getKey());
                });
      });
      // 发送数据
      sendPackage.sendInv();
    }
    long cost = System.currentTimeMillis() - starTime;
    logger.info("SPREAD finish one spread cost: {}ms", cost);
    if (cost < 1000){
      try {
        Thread.sleep(1000 - cost);
      } catch (Exception e) {
        logger.error("SPREAD error: {}", e.getMessage(), e);
      }
    }
  }

  /**
   * 收集交易
   *  广播在易在: consumerAdvToSpread 方法中
   *
   * @param msg 构造的交易
   */
  public void broadcast(Message msg) {
    long startTime = System.currentTimeMillis();

    InventoryType type = null;
    if (msg instanceof BlockMessage) {
      logger.info("Ready to broadcast block {}", ((BlockMessage) msg).getBlockId());
//      freshBlockId.offer(((BlockMessage) msg).getBlockId());
      blockCache.put(msg.getMessageId(), (BlockMessage) msg);
      type = InventoryType.BLOCK;
    } else if (msg instanceof TransactionMessage) {
      trxCache.put(msg.getMessageId(), (TransactionMessage) msg);
      type = InventoryType.TRX;
    }
    long currentTime = System.currentTimeMillis();
    long cost = (startTime - currentTime) / 1000;
    if (count.incrementAndGet() % 1000 == 0 && cost > 0) {
      logger.info("SPREAD count: {}, cost: {}s, Tps: {}, advObjToSpreadSize: {}",
              count.get(), cost, count.get() / cost, advObjToSpread.size());
    }

    synchronized (advObjToSpread) {
      advObjToSpread.put(msg.getMessageId(), type);
      if(advObjToSpread.size() % 100 == 0) {
        logger.info("advObjToSpread size " + advObjToSpread.size());
      }
    }
  }

  class InvSender {

    // 持有当前 ActivePeer 节点
    private HashMap<PeerConnection, HashMap<InventoryType, LinkedList<Sha256Hash>>> send
            = new HashMap<>();

    public void clear() {
      this.send.clear();
    }

    public void add(Map.Entry<Sha256Hash, InventoryType> id, PeerConnection peer) {
      // peer 节点存在，数据为空
      if (send.containsKey(peer) && !send.get(peer).containsKey(id.getValue())) {
        send.get(peer).put(id.getValue(), new LinkedList<>());
      } else if (!send.containsKey(peer)) {
        send.put(peer, new HashMap<>());
        send.get(peer).put(id.getValue(), new LinkedList<>());
      }
      send.get(peer).get(id.getValue()).offer(id.getKey());
    }

    // 发送数据
    public void sendInv() {
      send.forEach((peer, ids)-> {
        ids.forEach((key, value)-> {
          if (key.equals(InventoryType.BLOCK)) {
            value.sort(Comparator.comparingLong(value1 -> new BlockCapsule.BlockId(value1).getNum()));
          }
          // 发送，最终通过 netty 发送
          peer.sendMessage(new InventoryMessage(value, key));
          logger.info("SPREAD send inv to peer {} size: {}", peer.getInetAddress(), value.size());
        });
      });
    }

    // 查看 peer 下的数据大小
    public int getSize(PeerConnection peer) {
      if (send.containsKey(peer)) {
        return send.get(peer).values().stream().mapToInt(LinkedList::size).sum();
      }
      return 0;
    }
  }

}
