package okio

import okio.ByteString.Companion.decodeHex
import okio.internal.commonAsUtf8ToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals

class OkioMessageDigestTest {

  // region SHA-1

  @Test fun sha1EmptyValueHashIsCorrect() {
    // arrange act
    val result = ByteString.EMPTY.sha1()

    // assert
    assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709".decodeHex(), result)
  }

  @Test fun sha1SimpleValueHashIsCorrect() {
    // arrange
    val value = ByteString.of(*"Kevin".commonAsUtf8ToByteArray())

    // act
    val result = value.sha1()

    // assert
    assertEquals("e043899daa0c7add37bc99792b2c045d6abbc6dc".decodeHex(), result)
  }

  @Test fun sha1ChunkSizeValueHashIsCorrect() {
    // arrange
    val bytes = ByteArray(64) { 'i'.toByte() }
    val value = ByteString.of(*bytes)

    // act
    val result = value.sha1()

    // assert
    assertEquals("79c64455d4565a82bc3f4ec5d9a5e8443c2e77b3".decodeHex(), result)
  }

  @Test fun sha1ValueLargerThanChunkHashIsCorrect() {
    // arrange
    val bytes = ByteArray(65) { 'i'.toByte() }
    val value = ByteString.of(*bytes)

    // act
    val result = value.sha1()

    // assert
    assertEquals("6658b01c97fd6db0bdb010b2e154164285e5bd71".decodeHex(), result)
  }

  @Test fun sha1ComplexValueHashIsCorrect() {
    // arrange
    val value = ByteString.of(
      *"The quick brown fox jumps over the lazy dog".commonAsUtf8ToByteArray()
    )

    // act
    val result = value.sha1()

    // assert
    assertEquals("2fd4e1c67a2d28fced849ee1bb76e7391b93eb12".decodeHex(), result)
  }

  // endregion

  // region SHA-256

  @Test fun sha256EmptyValueHashIsCorrect() {
    // arrange act
    val result = ByteString.EMPTY.sha256()

    // assert
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855".decodeHex(), result)
  }

  @Test fun sha256SimpleValueHashIsCorrect() {
    // arrange
    val value = ByteString.of(*"hello world".commonAsUtf8ToByteArray())

    // act
    val result = value.sha256()

    // assert
    assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9".decodeHex(), result)
  }

  @Test fun sha256ChunkSizeValueHashIsCorrect() {
    // arrange
    val bytes = ByteArray(64) { 'i'.toByte() }
    val value = ByteString.of(*bytes)

    // act
    val result = value.sha256()

    // assert
    assertEquals("a343b617ce1070a37251a5e66b409947ec3d3ff7d89b9de482d7df84402778d2".decodeHex(), result)
  }

  @Test fun sha256ValueLargerThanChunkHashIsCorrect() {
    // arrange
    val bytes = ByteArray(65) { 'i'.toByte() }
    val value = ByteString.of(*bytes)

    // act
    val result = value.sha256()

    // assert
    assertEquals("7db92d77e8b1d5ac593cd614244109b70618fe2a6a7eba541a5347ff383237d0".decodeHex(), result)
  }

  @Test fun sha256ComplexValueHashIsCorrect() {
    // arrange
    val value = ByteString.of(
      *"The quick brown fox jumps over the lazy dog".commonAsUtf8ToByteArray()
    )

    // act
    val result = value.sha256()

    // assert
    assertEquals("d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592".decodeHex(), result)
  }

  // endregion

  // region SHA-512

  // endregion

  // region MD5

  // endregion
}