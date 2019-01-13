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
package org.apache.samza.job.model;

/**
 * This defines the logical mode of a taskInstance.
 * Active is the defacto mode for a task, i.e., tasks processing input, reading/writing state, producing output, etc.
 * StandbyState is the mode for tasks, that maintain warmed-up KV state by reading from its changelog.
 */
public enum TaskMode {
  Active("active"), StandbyState("standbyState");

  private final String name;

  private TaskMode(String s) {
    name = s;
  }

  public String toString() {
    return this.name;
  }
}