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
package org.apache.samza.diagnostics;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.samza.job.model.ContainerModel;
import org.apache.samza.metrics.reporter.Metrics;
import org.apache.samza.metrics.reporter.MetricsHeader;
import org.apache.samza.metrics.reporter.MetricsSnapshot;
import org.apache.samza.serializers.model.SamzaObjectMapper;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Defines the contents for any message emitted to the diagnostic stream by the {@link DiagnosticsManager}.
 * All contents are stored in a {@link MetricsHeader} and a metricsMessage map which combine to get a {@link MetricsSnapshot},
 * which can be serialized using serdes ({@link org.apache.samza.serializers.MetricsSnapshotSerdeV2}).
 * This class serializes {@link ContainerModel} using {@link SamzaObjectMapper} before adding to the metrics message.
 *
 */
public class DiagnosticsStreamMessage {
  private static final Logger LOG = LoggerFactory.getLogger(DiagnosticsStreamMessage.class);

  public static final String GROUP_NAME_FOR_DIAGNOSTICS_MANAGER = DiagnosticsManager.class.getName();
  // Using DiagnosticsManager as the group name for processor-stop-events, job-related params, and container model

  private static final String SAMZACONTAINER_METRICS_GROUP_NAME = "org.apache.samza.container.SamzaContainerMetrics";
  // Using SamzaContainerMetrics as the group name for exceptions to maintain compatibility with existing diagnostics
  private static final String EXCEPTION_LIST_METRIC_NAME = "exceptions";

  private static final String STOP_EVENT_LIST_METRIC_NAME = "stopEvents";
  private static final String CONTAINER_MB_METRIC_NAME = "containerMemoryMb";
  private static final String CONTAINER_NUM_CORES_METRIC_NAME = "containerNumCores";
  public static final String CONTAINER_NUM_STORES_WITH_CHANGELOG_METRIC_NAME = "numStoresWithChangelog";
  private static final String CONTAINER_MODELS_METRIC_NAME = "containerModels";

  private final MetricsHeader metricsHeader;
  private final Map<String, Map<String, Object>> metricsMessage;

  public DiagnosticsStreamMessage(String jobName, String jobId, String containerName, String executionEnvContainerId,
      String taskClassVersion, String samzaVersion, String hostname, long timestamp, long resetTimestamp) {

    // Create the metricHeader
    metricsHeader =
        new MetricsHeader(jobName, jobId, containerName, executionEnvContainerId, DiagnosticsManager.class.getName(),
            taskClassVersion, samzaVersion, hostname, timestamp, resetTimestamp);

    this.metricsMessage = new HashMap<>();
  }

  /**
   * Add the container memory mb parameter to the message.
   * @param containerMemoryMb the memory mb parameter value.
   */
  public void addContainerMb(Integer containerMemoryMb) {
    addToMetricsMessage(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER, CONTAINER_MB_METRIC_NAME, containerMemoryMb);
  }

  /**
   * Add the container num cores parameter to the message.
   * @param containerNumCores the num core parameter value.
   */
  public void addContainerNumCores(Integer containerNumCores) {
    addToMetricsMessage(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER, CONTAINER_NUM_CORES_METRIC_NAME, containerNumCores);
  }

  /**
   * Add the num stores with changelog parameter to the message.
   * @param numStoresWithChangelog the parameter value.
   */
  public void addNumStoresWithChangelog(Integer numStoresWithChangelog) {
    addToMetricsMessage(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER, CONTAINER_NUM_STORES_WITH_CHANGELOG_METRIC_NAME,
        numStoresWithChangelog);
  }

  /**
   * Add a map of container models (indexed by containerID) to the message.
   * @param containerModelMap the container models map
   */
  public void addContainerModels(Map<String, ContainerModel> containerModelMap) {
    if (containerModelMap != null && !containerModelMap.isEmpty()) {
      addToMetricsMessage(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER, CONTAINER_MODELS_METRIC_NAME,
          serializeContainerModelMap(containerModelMap));
    }
  }

  /**
   * Add a list of {@link DiagnosticsExceptionEvent}s to the message.
   * @param exceptionList the list to add.
   */
  public void addDiagnosticsExceptionEvents(Collection<DiagnosticsExceptionEvent> exceptionList) {
    if (exceptionList != null && !exceptionList.isEmpty()) {
      addToMetricsMessage(SAMZACONTAINER_METRICS_GROUP_NAME, EXCEPTION_LIST_METRIC_NAME, exceptionList);
    }
  }

  /**
   * Add a list of {@link org.apache.samza.diagnostics.ProcessorStopEvent}s to add to the list.
   * @param stopEventList the list to add.
   */
  public void addProcessorStopEvents(List<ProcessorStopEvent> stopEventList) {
    if (stopEventList != null && !stopEventList.isEmpty()) {
      addToMetricsMessage(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER, STOP_EVENT_LIST_METRIC_NAME, stopEventList);
    }
  }

  /**
   * Convert this message into a {@link MetricsSnapshot}, useful for serde-deserde using {@link org.apache.samza.serializers.MetricsSnapshotSerde}.
   * @return
   */
  public MetricsSnapshot convertToMetricsSnapshot() {
    MetricsSnapshot metricsSnapshot = new MetricsSnapshot(metricsHeader, new Metrics(metricsMessage));
    return metricsSnapshot;
  }

  /**
   * Check if the message has no contents.
   * @return True if the message is empty, false otherwise.
   */
  public boolean isEmpty() {
    return metricsMessage.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    DiagnosticsStreamMessage that = (DiagnosticsStreamMessage) o;
    return metricsHeader.getAsMap().equals(that.metricsHeader.getAsMap()) && metricsMessage.equals(that.metricsMessage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(metricsHeader, metricsMessage);
  }

  public Collection<ProcessorStopEvent> getProcessorStopEvents() {
    return (Collection<ProcessorStopEvent>) getFromMetricsMessage(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER,
        STOP_EVENT_LIST_METRIC_NAME);
  }

  public Collection<DiagnosticsExceptionEvent> getExceptionEvents() {
    return (Collection<DiagnosticsExceptionEvent>) getFromMetricsMessage(SAMZACONTAINER_METRICS_GROUP_NAME,
        EXCEPTION_LIST_METRIC_NAME);
  }

  public Integer getContainerMb() {
    return (Integer) getFromMetricsMessage(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER, CONTAINER_MB_METRIC_NAME);
  }

  public Integer getContainerNumCores() {
    return (Integer) getFromMetricsMessage(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER, CONTAINER_NUM_CORES_METRIC_NAME);
  }

  public Integer getNumStoresWithChangelog() {
    return (Integer) getFromMetricsMessage(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER,
        CONTAINER_NUM_STORES_WITH_CHANGELOG_METRIC_NAME);
  }

  public Map<String, ContainerModel> getContainerModels() {
    return deserializeContainerModelMap(
        (Map<String, Object>) getFromMetricsMessage(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER, CONTAINER_MODELS_METRIC_NAME));
  }

  // Helper method to get a {@link DiagnosticsStreamMessage} from a {@link MetricsSnapshot}.
  //   * This is typically used when deserializing messages from a diagnostics-stream.
  //   * @param metricsSnapshot
  public static DiagnosticsStreamMessage convertToDiagnosticsStreamMessage(MetricsSnapshot metricsSnapshot) {
    DiagnosticsStreamMessage diagnosticsStreamMessage =
        new DiagnosticsStreamMessage(metricsSnapshot.getHeader().getJobName(), metricsSnapshot.getHeader().getJobId(),
            metricsSnapshot.getHeader().getContainerName(), metricsSnapshot.getHeader().getExecEnvironmentContainerId(),
            metricsSnapshot.getHeader().getVersion(), metricsSnapshot.getHeader().getSamzaVersion(),
            metricsSnapshot.getHeader().getHost(), metricsSnapshot.getHeader().getTime(),
            metricsSnapshot.getHeader().getResetTime());

    Map<String, Map<String, Object>> metricsMap = metricsSnapshot.getMetrics().getAsMap();
    Map<String, Object> diagnosticsManagerGroupMap = metricsMap.get(GROUP_NAME_FOR_DIAGNOSTICS_MANAGER);
    Map<String, Object> containerMetricsGroupMap = metricsMap.get(SAMZACONTAINER_METRICS_GROUP_NAME);

    if (diagnosticsManagerGroupMap != null) {

      diagnosticsStreamMessage.addContainerNumCores((Integer) diagnosticsManagerGroupMap.get(CONTAINER_NUM_CORES_METRIC_NAME));
      diagnosticsStreamMessage.addContainerMb((Integer) diagnosticsManagerGroupMap.get(CONTAINER_MB_METRIC_NAME));
      diagnosticsStreamMessage.addNumStoresWithChangelog((Integer) diagnosticsManagerGroupMap.get(CONTAINER_NUM_STORES_WITH_CHANGELOG_METRIC_NAME));
      diagnosticsStreamMessage.addContainerModels(deserializeContainerModelMap(
          (Map<String, Object>) diagnosticsManagerGroupMap.get(CONTAINER_MODELS_METRIC_NAME)));

      diagnosticsStreamMessage.addProcessorStopEvents((List<ProcessorStopEvent>) diagnosticsManagerGroupMap.get(STOP_EVENT_LIST_METRIC_NAME));
    }

    if (containerMetricsGroupMap != null && containerMetricsGroupMap.containsKey(EXCEPTION_LIST_METRIC_NAME)) {
      diagnosticsStreamMessage.addDiagnosticsExceptionEvents(
          (Collection<DiagnosticsExceptionEvent>) containerMetricsGroupMap.get(EXCEPTION_LIST_METRIC_NAME));
    }

    return diagnosticsStreamMessage;
  }

  /**
   * Helper method to use {@link SamzaObjectMapper} to serialize {@link ContainerModel}s.
   * {@link SamzaObjectMapper} provides several conventions and optimizations for serializing containerModels.
   * @param containerModelMap map of container models to serialize.
   * @return
   */
  private static Map<String, String> serializeContainerModelMap(Map<String, ContainerModel> containerModelMap) {
    Map<String, String> serializedContainerModelMap = new HashMap<>();
    ObjectMapper samzaObjectMapper = SamzaObjectMapper.getObjectMapper();
    String serializedContainerModel;

    for (Map.Entry<String, ContainerModel> containerModelEntry : containerModelMap.entrySet()) {
      serializedContainerModel = "";
      try {
        serializedContainerModel = samzaObjectMapper.writeValueAsString(containerModelEntry.getValue());
      } catch (IOException e) {
        LOG.error("Exception in serializing container model ", e);
      } finally {
        serializedContainerModelMap.put(containerModelEntry.getKey(), serializedContainerModel);
      }
    }

    return serializedContainerModelMap;
  }

  /**
   * Helper method to use {@link SamzaObjectMapper} to deserialize {@link ContainerModel}s.
   * {@link SamzaObjectMapper} provides several conventions and optimizations for deserializing containerModels.
   * @return
   */
  private static Map<String, ContainerModel> deserializeContainerModelMap(
      Map<String, Object> serializedContainerModel) {
    Map<String, ContainerModel> containerModelMap = null;
    ObjectMapper samzaObjectMapper = SamzaObjectMapper.getObjectMapper();

    if (serializedContainerModel != null) {
      containerModelMap = new HashMap<>();
      for (Map.Entry<String, Object> containerModelEntry : serializedContainerModel.entrySet()) {
        try {
          ContainerModel containerModel =
              samzaObjectMapper.readValue(containerModelEntry.getValue().toString(), ContainerModel.class);
          containerModelMap.put(containerModelEntry.getKey(), containerModel);
        } catch (IOException e) {
          LOG.error("Exception in deserializing container model ", e);
        }
      }
    }

    return containerModelMap;
  }

  private void addToMetricsMessage(String groupName, String metricName, Object value) {
    if (value != null) {
      metricsMessage.putIfAbsent(groupName, new HashMap<>());
      metricsMessage.get(groupName).put(metricName, value);
    }
  }

  private Object getFromMetricsMessage(String groupName, String metricName) {
    if (metricsMessage.containsKey(groupName) && metricsMessage.get(groupName) != null) {
      return metricsMessage.get(groupName).get(metricName);
    } else {
      return null;
    }
  }
}
