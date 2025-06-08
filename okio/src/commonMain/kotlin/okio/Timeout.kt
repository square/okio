/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okio

/**
 * A policy on how much time to spend on a task before giving up. When a task times out, it is left
 * in an unspecified state and should be abandoned. For example, if reading from a source times out,
 * that source should be closed and the read should be retried later. If writing to a sink times
 * out, the same rules apply: close the sink and retry later.
 *
 * ### Timeouts and Deadlines
 *
 * This class offers two complementary controls to define a timeout policy.
 *
 * **Timeouts** specify the maximum time to wait for a single operation to complete. Timeouts are
 * typically used to detect problems like network partitions. For example, if a remote peer doesn't
 * return *any* data for ten seconds, we may assume that the peer is unavailable.
 *
 * **Deadlines** specify the maximum time to spend on a job, composed of one or more operations. Use
 * deadlines to set an upper bound on the time invested on a job. For example, a battery-conscious
 * app may limit how much time it spends pre-loading content.
 */
expect open class Timeout {
  companion object {
    /**
     * An empty timeout that neither tracks nor detects timeouts. Use this when timeouts aren't
     * necessary, such as in implementations whose operations do not block.
     */
    val NONE: Timeout
  }
}
