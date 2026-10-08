package dev.sdxcod.mqlab.transport;

import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;

import java.util.Hashtable;

@FunctionalInterface
public interface QueueManagerConnector {
    MQQueueManager connect(String name, Hashtable<String, Object> properties) throws MQException;
}
