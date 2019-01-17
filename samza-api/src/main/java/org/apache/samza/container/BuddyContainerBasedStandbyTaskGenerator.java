/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.samza.container;

import java.util.HashMap;
import java.util.Map;
import org.apache.samza.job.model.ContainerModel;
import org.apache.samza.job.model.TaskMode;
import org.apache.samza.job.model.TaskModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This StandbyTaskGenerator generates Standby-tasks and adds them to separate dedicated containers.
 * It adds (r-1) Standby-Tasks for each active task, where r is the replication factor.
 * Hence it adds r-1 additional containers.
 *
 * All Standby-tasks are assigned a TaskName with a "Standby" prefix.
 * The new containers carrying Standby tasks that are added are assigned containerIDs corresponding to its
 * active container, e.g., activeContainerID-replicaNumber
 * For e.g.,
 *
 * If the initial container model map is:
 *
 * Container 0 : (Partition 0, Partition 1)
 * Container 1 : (Partition 2, Partition 3)
 *
 * with replicationFactor = 3
 *
 * The generated containerModel map is:
 *
 * Container 0 : (Partition 0, Partition 1)
 * Container 1 : (Partition 2, Partition 3)
 *
 * Container 0-0 : (Standby Partition 0-0, Standby Partition 1-0)
 * Container 1-0 : (Standby Partition 2-0, Standby Partition 3-0)
 *
 * Container 0-1 : (Standby Partition 0-1, Standby Partition 1-1)
 * Container 1-1 : (Standby Partition 2-1, Standby Partition 3-1)
 *
 *
 */
public class BuddyContainerBasedStandbyTaskGenerator implements StandbyTaskGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(BuddyContainerBasedStandbyTaskGenerator.class);
  private static final String CONTAINER_ID_SEPARATOR = "-";
  private static final String TASKNAME_SEPARATOR = "-";
  private static final String STANDBY_TASKNAME_PREFIX = "Standby ";

  /**
   *  Generate a container model map with standby tasks added and grouped in buddy containers.
   *
   * @param containerModels The initial container model map.
   * @param replicationFactor The desired replication factor, if the replication-factor is n, we add n-1 standby tasks for each active task.
   * @return
   */
  @Override
  public Map<String, ContainerModel> generateStandbyTasks(Map<String, ContainerModel> containerModels,
      int replicationFactor) {
    LOG.debug("Received current containerModel map : {}, replicationFactor : {}", containerModels, replicationFactor);
    Map<String, ContainerModel> buddyContainerMap = new HashMap<>();

    for (String activeContainerId : containerModels.keySet()) {
      for (int replicaNum = 0; replicaNum < replicationFactor - 1; replicaNum++) {
        String buddyContainerId = getBuddyContainerId(activeContainerId, replicaNum);

        ContainerModel buddyContainerModel = new ContainerModel(buddyContainerId,
            getTaskModelForBuddyContainer(containerModels.get(activeContainerId).getTasks(), replicaNum));

        buddyContainerMap.put(buddyContainerId, buddyContainerModel);
      }
    }

    LOG.info("Adding buddy containers : {}", buddyContainerMap);
    buddyContainerMap.putAll(containerModels);
    return buddyContainerMap;
  }

  // Helper method to populate the container model for a buddy container.
  private static Map<TaskName, TaskModel> getTaskModelForBuddyContainer(
      Map<TaskName, TaskModel> activeContainerTaskModel, int replicaNum) {
    Map<TaskName, TaskModel> standbyTaskModels = new HashMap<>();

    for (TaskName taskName : activeContainerTaskModel.keySet()) {
      TaskName standbyTaskName = getStandbyTaskName(taskName, replicaNum);
      TaskModel standbyTaskModel =
          new TaskModel(standbyTaskName, activeContainerTaskModel.get(taskName).getSystemStreamPartitions(),
              activeContainerTaskModel.get(taskName).getChangelogPartition(), TaskMode.Standby);

      standbyTaskModels.put(standbyTaskName, standbyTaskModel);
    }

    LOG.info("Generated standbyTaskModels : {} for active task models : {}", standbyTaskModels,
        activeContainerTaskModel);
    return standbyTaskModels;
  }

  // Helper method to generate buddy containerIDs by appending the replica-number to the active-container's id.
  private final static String getBuddyContainerId(String activeContainerId, int replicaNumber) {
    return activeContainerId.concat(CONTAINER_ID_SEPARATOR).concat(String.valueOf(replicaNumber));
  }

  // Helper method to get the standby task name by prefixing "Standby" to the corresponding active task's name.
  private final static TaskName getStandbyTaskName(TaskName activeTaskName, int replicaNum) {
    return new TaskName(STANDBY_TASKNAME_PREFIX.concat(activeTaskName.getTaskName())
        .concat(TASKNAME_SEPARATOR)
        .concat(String.valueOf(replicaNum)));
  }
}
