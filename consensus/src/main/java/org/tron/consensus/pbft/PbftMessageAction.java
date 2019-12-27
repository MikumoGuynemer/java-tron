package org.tron.consensus.pbft;

import com.google.protobuf.ByteString;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tron.consensus.base.Param;
import org.tron.consensus.pbft.message.PbftBaseMessage;
import org.tron.consensus.pbft.message.PbftBlockMessage;
import org.tron.consensus.pbft.message.PbftSrMessage;
import org.tron.core.capsule.PbftSignCapsule;
import org.tron.core.db.CommonDataBase;
import org.tron.core.db.PbftSignDataStore;
import org.tron.core.store.DynamicPropertiesStore;
import org.tron.protos.Protocol.PbftMessage.Raw;
import org.tron.protos.Protocol.SrList;

@Slf4j(topic = "pbft")
@Component
public class PbftMessageAction {

  private long checkPoint = 0;

  @Autowired
  private CommonDataBase commonDataBase;
  @Autowired
  private DynamicPropertiesStore dynamicPropertiesStore;
  @Autowired
  private PbftSignDataStore pbftSignDataStore;

  public void action(PbftBaseMessage message, List<ByteString> dataSignList) {
    switch (message.getType()) {
      case PBFT_BLOCK_MSG: {
        PbftBlockMessage blockMessage = (PbftBlockMessage) message;
        long blockNum = blockMessage.getBlockNum();
        if (blockNum - checkPoint >= Param.getInstance().getCheckMsgCount()) {
          checkPoint = blockNum;
          commonDataBase.saveLatestPbftBlockNum(blockNum);
          Raw raw = blockMessage.getPbftMessage().getRawData();
          commonDataBase.saveLatestPbftBlockHash(raw.getData().toByteArray());
          pbftSignDataStore
              .putBlockSignData(blockNum, new PbftSignCapsule(raw.getData(), dataSignList));
          logger.info("commit msg block num is:{}", blockNum);
        }
      }
      break;
      case PBFT_SR_MSG: {
        try {
          PbftSrMessage srMessage = (PbftSrMessage) message;
          SrList srList = SrList
              .parseFrom(srMessage.getPbftMessage().getRawData().getData().toByteArray());
          Raw raw = srMessage.getPbftMessage().getRawData();
          pbftSignDataStore
              .putSrSignData(srList.getCycle(), new PbftSignCapsule(raw.getData(), dataSignList));
          logger.info("sr commit msg :{}, cycle:{}", srMessage.getBlockNum(), srList.getCycle());
        } catch (Exception e) {
          logger.error("process the sr list error!", e);
        }
      }
      break;
      default:
        break;
    }
  }

}