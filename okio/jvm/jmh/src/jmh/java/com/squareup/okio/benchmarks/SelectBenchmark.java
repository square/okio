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
package com.squareup.okio.benchmarks;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import okio.Buffer;
import okio.ByteString;
import okio.Options;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.RunnerException;

@Fork(1)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class SelectBenchmark {
  /** Representative sample field names as one might find in a JSON document. */
  List<String> sampleValues = Arrays.asList("id", "name", "description", "type", "sku_ids",
      "offers", "start_time", "end_time", "expires", "start_of_availability", "duration",
      "allow_recording", "thumbnail_id", "thumbnail_formats", "is_episode", "is_live", "channel_id",
      "genre_list", "provider_networks", "year", "video_flags", "is_repeat", "series_id",
      "series_name", "series_description", "original_air_date", "letter_box", "category",
      "child_protection_rating", "parental_control_minimum_age", "images", "episode_id",
      "season_number", "episode_number", "directors_list", "scriptwriters_list", "actors_list",
      "drm_rights", "is_location_chk_reqd", "is_catchup_enabled", "catchup_duration",
      "is_timeshift_enabled", "timeshift_duration", "is_startover_enabled", "is_recording_enabled",
      "suspension_time", "shared_ref_id", "linked_channel_number", "audio_lang", "subcategory",
      "metadata_root_id", "ref_id", "ref_type", "display_position", "thumbnail_format_list",
      "network", "external_url", "offer_type", "em_format", "em_artist_name", "assets",
      "media_class", "media_id", "channel_number");

  @Param({ "4", "8", "16", "32", "64" })
  int optionCount;

  @Param({ "2048" })
  int selectCount;

  Buffer buffer = new Buffer();
  Options options;
  ByteString sampleData;

  @Setup
  public void setup() throws IOException {
    ByteString[] byteStrings = new ByteString[optionCount];
    for (int i = 0; i < optionCount; i++) {
      byteStrings[i] = ByteString.encodeUtf8(sampleValues.get(i) + "\"");
    }
    options = Options.of(byteStrings);

    Random dice = new Random(0);
    Buffer sampleDataBuffer = new Buffer();
    for (int i = 0; i < selectCount; i++) {
      sampleDataBuffer.write(byteStrings[dice.nextInt(optionCount)]);
    }
    sampleData = sampleDataBuffer.readByteString();
  }

  @Benchmark
  public void select() throws IOException {
    buffer.write(sampleData);
    for (int i = 0; i < selectCount; i++) {
      buffer.select(options);
    }
    if (!buffer.exhausted()) throw new AssertionError();
  }

  public static void main(String[] args) throws IOException, RunnerException {
    Main.main(new String[] {
        SelectBenchmark.class.getName()
    });
  }
}
