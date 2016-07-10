package okio;

import java.io.EOFException;
import java.nio.charset.Charset;

enum ByteOrderMark {
  BOM_UTF_8(Util.UTF_8, ByteString.decodeHex("EFBBBF")),
  BOM_UTF_16_BE(Charset.forName("UTF-16BE"), ByteString.decodeHex("FEFF")),
  BOM_UTF_16_LE(Charset.forName("UTF-16LE"), ByteString.decodeHex("FFFE")),
  BOM_UTF_32_BE(Charset.forName("UTF-32BE"), ByteString.decodeHex("0000FEFF")),
  BOM_UTF_32_LE(Charset.forName("UTF-32LE"), ByteString.decodeHex("FFFE0000"));

  static final int MAX_BOM_LENGTH = 4;

  final Charset charset;
  final ByteString bom;

  ByteOrderMark(Charset charset, ByteString bom) {
    this.charset = charset;
    this.bom = bom;
  }

  void skip(Buffer buffer) throws EOFException {
    buffer.skip(bom.size());
  }

  static ByteOrderMark getByteOrderMark(Buffer buffer, ByteOrderMark... byteOrderMarks) {
    for (ByteOrderMark byteOrderMark : byteOrderMarks) {
      if (buffer.rangeEquals(0, byteOrderMark.bom)) {
        return byteOrderMark;
      }
    }

    return null;
  }
}
