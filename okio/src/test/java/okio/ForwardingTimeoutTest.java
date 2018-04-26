/*
 * Copyright (C) 2018 Square, Inc.
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
package okio;

import java.util.concurrent.TimeUnit;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ForwardingTimeoutTest {
  @Test public void getAndSetDelegate() {
    Timeout timeout1 = new Timeout();
    Timeout timeout2 = new Timeout();

    ForwardingTimeout forwardingTimeout = new ForwardingTimeout(timeout1);
    forwardingTimeout.timeout(5, TimeUnit.SECONDS);
    assertThat(timeout1.timeoutNanos()).isNotEqualTo(0L);
    assertThat(timeout2.timeoutNanos()).isEqualTo(0L);
    forwardingTimeout.clearTimeout();
    assertThat(timeout1.timeoutNanos()).isEqualTo(0L);
    assertThat(timeout2.timeoutNanos()).isEqualTo(0L);
    assertThat(forwardingTimeout.delegate()).isEqualTo(timeout1);

    assertThat(forwardingTimeout.setDelegate(timeout2)).isEqualTo(forwardingTimeout);
    forwardingTimeout.timeout(5, TimeUnit.SECONDS);
    assertThat(timeout1.timeoutNanos()).isEqualTo(0L);
    assertThat(timeout2.timeoutNanos()).isNotEqualTo(0L);
    forwardingTimeout.clearTimeout();
    assertThat(timeout1.timeoutNanos()).isEqualTo(0L);
    assertThat(timeout2.timeoutNanos()).isEqualTo(0L);
    assertThat(forwardingTimeout.delegate()).isEqualTo(timeout2);
  }
}
