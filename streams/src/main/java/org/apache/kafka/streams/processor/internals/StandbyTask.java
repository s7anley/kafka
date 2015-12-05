/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.streams.processor.internals;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.streams.StreamingConfig;
import org.apache.kafka.streams.StreamingMetrics;
import org.apache.kafka.streams.processor.TaskId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A StandbyTask
 */
public class StandbyTask extends AbstractTask {

    private static final Logger log = LoggerFactory.getLogger(StandbyTask.class);

    private final Map<TopicPartition, Long> checkpointedOffsets;

    /**
     * Create {@link StandbyTask} with its assigned partitions
     *
     * @param id                    the ID of this task
     * @param restoreConsumer       the instance of {@link Consumer} used when restoring state
     * @param topology              the instance of {@link ProcessorTopology}
     * @param config                the {@link StreamingConfig} specified by the user
     * @param metrics               the {@link StreamingMetrics} created by the thread
     */
    public StandbyTask(TaskId id,
                       Collection<TopicPartition> partitions,
                       ProcessorTopology topology,
                       Consumer<byte[], byte[]> consumer,
                       Consumer<byte[], byte[]> restoreConsumer,
                       StreamingConfig config,
                       StreamingMetrics metrics) {
        super(id, partitions, topology, consumer, restoreConsumer, config, true);

        // initialize the topology with its own context
        this.processorContext = new StandbyContextImpl(id, config, stateMgr, metrics);

        initializeStateStores();

        ((StandbyContextImpl) this.processorContext).initialized();

        this.checkpointedOffsets = Collections.unmodifiableMap(stateMgr.checkpointedOffsets());

        // set initial offset limits
        initializeOffsetLimits();
    }

    public Map<TopicPartition, Long> checkpointedOffsets() {
        return checkpointedOffsets;
    }

    public Collection<TopicPartition> changeLogPartitions() {
        return checkpointedOffsets.keySet();
    }

    /**
     * Updates a state store using records from one change log partition
     * @return a list of records not consumed
     */
    public List<ConsumerRecord<byte[], byte[]>> update(TopicPartition partition, List<ConsumerRecord<byte[], byte[]>> records) {
        return stateMgr.updateStandbyStates(partition, records);
    }

    public void commit() {
        stateMgr.flush();

        // reinitialize offset limits
        initializeOffsetLimits();
    }

    protected void initializeOffsetLimits() {
        for (TopicPartition partition : partitions) {
            OffsetAndMetadata metadata = consumer.committed(partition); // TODO: batch API?
            stateMgr.putOffsetLimit(partition, metadata != null ? metadata.offset() : 0L);
        }
    }

}