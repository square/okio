/*
 * Copyright (C) 2020 Square, Inc.
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

@ExperimentalFileSystem
internal expect val PLATFORM_TEMPORARY_DIRECTORY: Path

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantDelete(path: Path)

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantMkdir(dir: Path): Int

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantCanonicalize(path: Path): Path

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantMetadataOrNull(path: Path): FileMetadata?

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantMove(source: Path, target: Path)

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantSource(file: Path): Source

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantSink(file: Path): Sink

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantAppendingSink(file: Path): Sink

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantOpenReadOnly(file: Path): FileHandle

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantOpenReadWrite(file: Path): FileHandle

@ExperimentalFileSystem
internal expect fun PosixFileSystem.variantCreateSymlink(source: Path, target: Path)
