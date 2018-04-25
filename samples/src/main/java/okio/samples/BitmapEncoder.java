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
package okio.samples;

import java.io.File;
import java.io.IOException;
import okio.BufferedSink;
import okio.Okio;

public final class BitmapEncoder {
  static final class Bitmap {
    private final int[][] pixels;

    Bitmap(int[][] pixels) {
      this.pixels = pixels;
    }

    int width() {
      return pixels[0].length;
    }

    int height() {
      return pixels.length;
    }

    int red(int x, int y) {
      return (pixels[y][x] & 0xff0000) >> 16;
    }

    int green(int x, int y) {
      return (pixels[y][x] & 0xff00) >> 8;
    }

    int blue(int x, int y) {
      return (pixels[y][x] & 0xff);
    }
  }

  /**
   * Returns a bitmap that lights up red subpixels at the bottom, green subpixels on the right, and
   * blue subpixels in bottom-right.
   */
  Bitmap generateGradient() {
    int[][] pixels = new int[1080][1920];
    for (int y = 0; y < 1080; y++) {
      for (int x = 0; x < 1920; x++) {
        int r = (int) (y / 1080f * 255);
        int g = (int) (x / 1920f * 255);
        int b = (int) ((Math.hypot(x, y) / Math.hypot(1080, 1920)) * 255);
        pixels[y][x] = r << 16 | g << 8 | b;
      }
    }
    return new Bitmap(pixels);
  }

  void encode(Bitmap bitmap, File file) throws IOException {
    try (BufferedSink sink = Okio.buffer(Okio.sink(file))) {
      encode(bitmap, sink);
    }
  }

  /** https://en.wikipedia.org/wiki/BMP_file_format */
  void encode(Bitmap bitmap, BufferedSink sink) throws IOException {
    int height = bitmap.height();
    int width = bitmap.width();

    int bytesPerPixel = 3;
    int rowByteCountWithoutPadding = (bytesPerPixel * width);
    int rowByteCount = ((rowByteCountWithoutPadding + 3) / 4) * 4;
    int pixelDataSize = rowByteCount * height;
    int bmpHeaderSize = 14;
    int dibHeaderSize = 40;

    // BMP Header
    sink.writeUtf8("BM"); // ID.
    sink.writeIntLe(bmpHeaderSize + dibHeaderSize + pixelDataSize); // File size.
    sink.writeShortLe(0); // Unused.
    sink.writeShortLe(0); // Unused.
    sink.writeIntLe(bmpHeaderSize + dibHeaderSize); // Offset of pixel data.

    // DIB Header
    sink.writeIntLe(dibHeaderSize);
    sink.writeIntLe(width);
    sink.writeIntLe(height);
    sink.writeShortLe(1);  // Color plane count.
    sink.writeShortLe(bytesPerPixel * Byte.SIZE);
    sink.writeIntLe(0);    // No compression.
    sink.writeIntLe(16);   // Size of bitmap data including padding.
    sink.writeIntLe(2835); // Horizontal print resolution in pixels/meter. (72 dpi).
    sink.writeIntLe(2835); // Vertical print resolution in pixels/meter. (72 dpi).
    sink.writeIntLe(0);    // Palette color count.
    sink.writeIntLe(0);    // 0 important colors.

    // Pixel data.
    for (int y = height - 1; y >= 0; y--) {
      for (int x = 0; x < width; x++) {
        sink.writeByte(bitmap.blue(x, y));
        sink.writeByte(bitmap.green(x, y));
        sink.writeByte(bitmap.red(x, y));
      }

      // Padding for 4-byte alignment.
      for (int p = rowByteCountWithoutPadding; p < rowByteCount; p++) {
        sink.writeByte(0);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    BitmapEncoder encoder = new BitmapEncoder();
    Bitmap bitmap = encoder.generateGradient();
    encoder.encode(bitmap, new File("gradient.bmp"));
  }
}
