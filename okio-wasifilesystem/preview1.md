# Types
## <a href="#size" name="size"></a> `size`: `u32`

Size: 4

Alignment: 4

## <a href="#filesize" name="filesize"></a> `filesize`: `u64`
Non-negative file size or length of a region within a file.

Size: 8

Alignment: 8

## <a href="#timestamp" name="timestamp"></a> `timestamp`: `u64`
Timestamp in nanoseconds.

Size: 8

Alignment: 8

## <a href="#clockid" name="clockid"></a> `clockid`: `Variant`
Identifiers for clocks.

Size: 4

Alignment: 4

### Variant cases
- <a href="#clockid.realtime" name="clockid.realtime"></a> `realtime`
  The clock measuring real time. Time value zero corresponds with
  1970-01-01T00:00:00Z.

- <a href="#clockid.monotonic" name="clockid.monotonic"></a> `monotonic`
  The store-wide monotonic clock, which is defined as a clock measuring
  real time, whose value cannot be adjusted and which cannot have negative
  clock jumps. The epoch of this clock is undefined. The absolute time
  value of this clock therefore has no meaning.

- <a href="#clockid.process_cputime_id" name="clockid.process_cputime_id"></a> `process_cputime_id`
  The CPU-time clock associated with the current process.

- <a href="#clockid.thread_cputime_id" name="clockid.thread_cputime_id"></a> `thread_cputime_id`
  The CPU-time clock associated with the current thread.

## <a href="#errno" name="errno"></a> `errno`: `Variant`
Error codes returned by functions.
Not all of these error codes are returned by the functions provided by this
API; some are used in higher-level library layers, and others are provided
merely for alignment with POSIX.

Size: 2

Alignment: 2

### Variant cases
- <a href="#errno.success" name="errno.success"></a> `success`
  No error occurred. System call completed successfully.

- <a href="#errno.2big" name="errno.2big"></a> `2big`
  Argument list too long.

- <a href="#errno.acces" name="errno.acces"></a> `acces`
  Permission denied.

- <a href="#errno.addrinuse" name="errno.addrinuse"></a> `addrinuse`
  Address in use.

- <a href="#errno.addrnotavail" name="errno.addrnotavail"></a> `addrnotavail`
  Address not available.

- <a href="#errno.afnosupport" name="errno.afnosupport"></a> `afnosupport`
  Address family not supported.

- <a href="#errno.again" name="errno.again"></a> `again`
  Resource unavailable, or operation would block.

- <a href="#errno.already" name="errno.already"></a> `already`
  Connection already in progress.

- <a href="#errno.badf" name="errno.badf"></a> `badf`
  Bad file descriptor.

- <a href="#errno.badmsg" name="errno.badmsg"></a> `badmsg`
  Bad message.

- <a href="#errno.busy" name="errno.busy"></a> `busy`
  Device or resource busy.

- <a href="#errno.canceled" name="errno.canceled"></a> `canceled`
  Operation canceled.

- <a href="#errno.child" name="errno.child"></a> `child`
  No child processes.

- <a href="#errno.connaborted" name="errno.connaborted"></a> `connaborted`
  Connection aborted.

- <a href="#errno.connrefused" name="errno.connrefused"></a> `connrefused`
  Connection refused.

- <a href="#errno.connreset" name="errno.connreset"></a> `connreset`
  Connection reset.

- <a href="#errno.deadlk" name="errno.deadlk"></a> `deadlk`
  Resource deadlock would occur.

- <a href="#errno.destaddrreq" name="errno.destaddrreq"></a> `destaddrreq`
  Destination address required.

- <a href="#errno.dom" name="errno.dom"></a> `dom`
  Mathematics argument out of domain of function.

- <a href="#errno.dquot" name="errno.dquot"></a> `dquot`
  Reserved.

- <a href="#errno.exist" name="errno.exist"></a> `exist`
  File exists.

- <a href="#errno.fault" name="errno.fault"></a> `fault`
  Bad address.

- <a href="#errno.fbig" name="errno.fbig"></a> `fbig`
  File too large.

- <a href="#errno.hostunreach" name="errno.hostunreach"></a> `hostunreach`
  Host is unreachable.

- <a href="#errno.idrm" name="errno.idrm"></a> `idrm`
  Identifier removed.

- <a href="#errno.ilseq" name="errno.ilseq"></a> `ilseq`
  Illegal byte sequence.

- <a href="#errno.inprogress" name="errno.inprogress"></a> `inprogress`
  Operation in progress.

- <a href="#errno.intr" name="errno.intr"></a> `intr`
  Interrupted function.

- <a href="#errno.inval" name="errno.inval"></a> `inval`
  Invalid argument.

- <a href="#errno.io" name="errno.io"></a> `io`
  I/O error.

- <a href="#errno.isconn" name="errno.isconn"></a> `isconn`
  Socket is connected.

- <a href="#errno.isdir" name="errno.isdir"></a> `isdir`
  Is a directory.

- <a href="#errno.loop" name="errno.loop"></a> `loop`
  Too many levels of symbolic links.

- <a href="#errno.mfile" name="errno.mfile"></a> `mfile`
  File descriptor value too large.

- <a href="#errno.mlink" name="errno.mlink"></a> `mlink`
  Too many links.

- <a href="#errno.msgsize" name="errno.msgsize"></a> `msgsize`
  Message too large.

- <a href="#errno.multihop" name="errno.multihop"></a> `multihop`
  Reserved.

- <a href="#errno.nametoolong" name="errno.nametoolong"></a> `nametoolong`
  Filename too long.

- <a href="#errno.netdown" name="errno.netdown"></a> `netdown`
  Network is down.

- <a href="#errno.netreset" name="errno.netreset"></a> `netreset`
  Connection aborted by network.

- <a href="#errno.netunreach" name="errno.netunreach"></a> `netunreach`
  Network unreachable.

- <a href="#errno.nfile" name="errno.nfile"></a> `nfile`
  Too many files open in system.

- <a href="#errno.nobufs" name="errno.nobufs"></a> `nobufs`
  No buffer space available.

- <a href="#errno.nodev" name="errno.nodev"></a> `nodev`
  No such device.

- <a href="#errno.noent" name="errno.noent"></a> `noent`
  No such file or directory.

- <a href="#errno.noexec" name="errno.noexec"></a> `noexec`
  Executable file format error.

- <a href="#errno.nolck" name="errno.nolck"></a> `nolck`
  No locks available.

- <a href="#errno.nolink" name="errno.nolink"></a> `nolink`
  Reserved.

- <a href="#errno.nomem" name="errno.nomem"></a> `nomem`
  Not enough space.

- <a href="#errno.nomsg" name="errno.nomsg"></a> `nomsg`
  No message of the desired type.

- <a href="#errno.noprotoopt" name="errno.noprotoopt"></a> `noprotoopt`
  Protocol not available.

- <a href="#errno.nospc" name="errno.nospc"></a> `nospc`
  No space left on device.

- <a href="#errno.nosys" name="errno.nosys"></a> `nosys`
  Function not supported.

- <a href="#errno.notconn" name="errno.notconn"></a> `notconn`
  The socket is not connected.

- <a href="#errno.notdir" name="errno.notdir"></a> `notdir`
  Not a directory or a symbolic link to a directory.

- <a href="#errno.notempty" name="errno.notempty"></a> `notempty`
  Directory not empty.

- <a href="#errno.notrecoverable" name="errno.notrecoverable"></a> `notrecoverable`
  State not recoverable.

- <a href="#errno.notsock" name="errno.notsock"></a> `notsock`
  Not a socket.

- <a href="#errno.notsup" name="errno.notsup"></a> `notsup`
  Not supported, or operation not supported on socket.

- <a href="#errno.notty" name="errno.notty"></a> `notty`
  Inappropriate I/O control operation.

- <a href="#errno.nxio" name="errno.nxio"></a> `nxio`
  No such device or address.

- <a href="#errno.overflow" name="errno.overflow"></a> `overflow`
  Value too large to be stored in data type.

- <a href="#errno.ownerdead" name="errno.ownerdead"></a> `ownerdead`
  Previous owner died.

- <a href="#errno.perm" name="errno.perm"></a> `perm`
  Operation not permitted.

- <a href="#errno.pipe" name="errno.pipe"></a> `pipe`
  Broken pipe.

- <a href="#errno.proto" name="errno.proto"></a> `proto`
  Protocol error.

- <a href="#errno.protonosupport" name="errno.protonosupport"></a> `protonosupport`
  Protocol not supported.

- <a href="#errno.prototype" name="errno.prototype"></a> `prototype`
  Protocol wrong type for socket.

- <a href="#errno.range" name="errno.range"></a> `range`
  Result too large.

- <a href="#errno.rofs" name="errno.rofs"></a> `rofs`
  Read-only file system.

- <a href="#errno.spipe" name="errno.spipe"></a> `spipe`
  Invalid seek.

- <a href="#errno.srch" name="errno.srch"></a> `srch`
  No such process.

- <a href="#errno.stale" name="errno.stale"></a> `stale`
  Reserved.

- <a href="#errno.timedout" name="errno.timedout"></a> `timedout`
  Connection timed out.

- <a href="#errno.txtbsy" name="errno.txtbsy"></a> `txtbsy`
  Text file busy.

- <a href="#errno.xdev" name="errno.xdev"></a> `xdev`
  Cross-device link.

- <a href="#errno.notcapable" name="errno.notcapable"></a> `notcapable`
  Extension: Capabilities insufficient.

## <a href="#rights" name="rights"></a> `rights`: `Record`
File descriptor rights, determining which actions may be performed.

Size: 8

Alignment: 8

### Record members
- <a href="#rights.fd_datasync" name="rights.fd_datasync"></a> `fd_datasync`: `bool`
  The right to invoke [`fd_datasync`](#fd_datasync).
  If [`path_open`](#path_open) is set, includes the right to invoke
  [`path_open`](#path_open) with [`fdflags::dsync`](#fdflags.dsync).

Bit: 0

- <a href="#rights.fd_read" name="rights.fd_read"></a> `fd_read`: `bool`
  The right to invoke [`fd_read`](#fd_read) and [`sock_recv`](#sock_recv).
  If [`rights::fd_seek`](#rights.fd_seek) is set, includes the right to invoke [`fd_pread`](#fd_pread).

Bit: 1

- <a href="#rights.fd_seek" name="rights.fd_seek"></a> `fd_seek`: `bool`
  The right to invoke [`fd_seek`](#fd_seek). This flag implies [`rights::fd_tell`](#rights.fd_tell).

Bit: 2

- <a href="#rights.fd_fdstat_set_flags" name="rights.fd_fdstat_set_flags"></a> `fd_fdstat_set_flags`: `bool`
  The right to invoke [`fd_fdstat_set_flags`](#fd_fdstat_set_flags).

Bit: 3

- <a href="#rights.fd_sync" name="rights.fd_sync"></a> `fd_sync`: `bool`
  The right to invoke [`fd_sync`](#fd_sync).
  If [`path_open`](#path_open) is set, includes the right to invoke
  [`path_open`](#path_open) with [`fdflags::rsync`](#fdflags.rsync) and [`fdflags::dsync`](#fdflags.dsync).

Bit: 4

- <a href="#rights.fd_tell" name="rights.fd_tell"></a> `fd_tell`: `bool`
  The right to invoke [`fd_seek`](#fd_seek) in such a way that the file offset
  remains unaltered (i.e., [`whence::cur`](#whence.cur) with offset zero), or to
  invoke [`fd_tell`](#fd_tell).

Bit: 5

- <a href="#rights.fd_write" name="rights.fd_write"></a> `fd_write`: `bool`
  The right to invoke [`fd_write`](#fd_write) and [`sock_send`](#sock_send).
  If [`rights::fd_seek`](#rights.fd_seek) is set, includes the right to invoke [`fd_pwrite`](#fd_pwrite).

Bit: 6

- <a href="#rights.fd_advise" name="rights.fd_advise"></a> `fd_advise`: `bool`
  The right to invoke [`fd_advise`](#fd_advise).

Bit: 7

- <a href="#rights.fd_allocate" name="rights.fd_allocate"></a> `fd_allocate`: `bool`
  The right to invoke [`fd_allocate`](#fd_allocate).

Bit: 8

- <a href="#rights.path_create_directory" name="rights.path_create_directory"></a> `path_create_directory`: `bool`
  The right to invoke [`path_create_directory`](#path_create_directory).

Bit: 9

- <a href="#rights.path_create_file" name="rights.path_create_file"></a> `path_create_file`: `bool`
  If [`path_open`](#path_open) is set, the right to invoke [`path_open`](#path_open) with [`oflags::creat`](#oflags.creat).

Bit: 10

- <a href="#rights.path_link_source" name="rights.path_link_source"></a> `path_link_source`: `bool`
  The right to invoke [`path_link`](#path_link) with the file descriptor as the
  source directory.

Bit: 11

- <a href="#rights.path_link_target" name="rights.path_link_target"></a> `path_link_target`: `bool`
  The right to invoke [`path_link`](#path_link) with the file descriptor as the
  target directory.

Bit: 12

- <a href="#rights.path_open" name="rights.path_open"></a> `path_open`: `bool`
  The right to invoke [`path_open`](#path_open).

Bit: 13

- <a href="#rights.fd_readdir" name="rights.fd_readdir"></a> `fd_readdir`: `bool`
  The right to invoke [`fd_readdir`](#fd_readdir).

Bit: 14

- <a href="#rights.path_readlink" name="rights.path_readlink"></a> `path_readlink`: `bool`
  The right to invoke [`path_readlink`](#path_readlink).

Bit: 15

- <a href="#rights.path_rename_source" name="rights.path_rename_source"></a> `path_rename_source`: `bool`
  The right to invoke [`path_rename`](#path_rename) with the file descriptor as the source directory.

Bit: 16

- <a href="#rights.path_rename_target" name="rights.path_rename_target"></a> `path_rename_target`: `bool`
  The right to invoke [`path_rename`](#path_rename) with the file descriptor as the target directory.

Bit: 17

- <a href="#rights.path_filestat_get" name="rights.path_filestat_get"></a> `path_filestat_get`: `bool`
  The right to invoke [`path_filestat_get`](#path_filestat_get).

Bit: 18

- <a href="#rights.path_filestat_set_size" name="rights.path_filestat_set_size"></a> `path_filestat_set_size`: `bool`
  The right to change a file's size.
  If [`path_open`](#path_open) is set, includes the right to invoke [`path_open`](#path_open) with [`oflags::trunc`](#oflags.trunc).
  Note: there is no function named `path_filestat_set_size`. This follows POSIX design,
  which only has `ftruncate` and does not provide `ftruncateat`.
  While such function would be desirable from the API design perspective, there are virtually
  no use cases for it since no code written for POSIX systems would use it.
  Moreover, implementing it would require multiple syscalls, leading to inferior performance.

Bit: 19

- <a href="#rights.path_filestat_set_times" name="rights.path_filestat_set_times"></a> `path_filestat_set_times`: `bool`
  The right to invoke [`path_filestat_set_times`](#path_filestat_set_times).

Bit: 20

- <a href="#rights.fd_filestat_get" name="rights.fd_filestat_get"></a> `fd_filestat_get`: `bool`
  The right to invoke [`fd_filestat_get`](#fd_filestat_get).

Bit: 21

- <a href="#rights.fd_filestat_set_size" name="rights.fd_filestat_set_size"></a> `fd_filestat_set_size`: `bool`
  The right to invoke [`fd_filestat_set_size`](#fd_filestat_set_size).

Bit: 22

- <a href="#rights.fd_filestat_set_times" name="rights.fd_filestat_set_times"></a> `fd_filestat_set_times`: `bool`
  The right to invoke [`fd_filestat_set_times`](#fd_filestat_set_times).

Bit: 23

- <a href="#rights.path_symlink" name="rights.path_symlink"></a> `path_symlink`: `bool`
  The right to invoke [`path_symlink`](#path_symlink).

Bit: 24

- <a href="#rights.path_remove_directory" name="rights.path_remove_directory"></a> `path_remove_directory`: `bool`
  The right to invoke [`path_remove_directory`](#path_remove_directory).

Bit: 25

- <a href="#rights.path_unlink_file" name="rights.path_unlink_file"></a> `path_unlink_file`: `bool`
  The right to invoke [`path_unlink_file`](#path_unlink_file).

Bit: 26

- <a href="#rights.poll_fd_readwrite" name="rights.poll_fd_readwrite"></a> `poll_fd_readwrite`: `bool`
  If [`rights::fd_read`](#rights.fd_read) is set, includes the right to invoke [`poll_oneoff`](#poll_oneoff) to subscribe to [`eventtype::fd_read`](#eventtype.fd_read).
  If [`rights::fd_write`](#rights.fd_write) is set, includes the right to invoke [`poll_oneoff`](#poll_oneoff) to subscribe to [`eventtype::fd_write`](#eventtype.fd_write).

Bit: 27

- <a href="#rights.sock_shutdown" name="rights.sock_shutdown"></a> `sock_shutdown`: `bool`
  The right to invoke [`sock_shutdown`](#sock_shutdown).

Bit: 28

- <a href="#rights.sock_accept" name="rights.sock_accept"></a> `sock_accept`: `bool`
  The right to invoke [`sock_accept`](#sock_accept).

Bit: 29

## <a href="#fd" name="fd"></a> `fd`: `Handle`
A file descriptor handle.

Size: 4

Alignment: 4

### Supertypes
## <a href="#iovec" name="iovec"></a> `iovec`: `Record`
A region of memory for scatter/gather reads.

Size: 8

Alignment: 4

### Record members
- <a href="#iovec.buf" name="iovec.buf"></a> `buf`: `Pointer<u8>`
  The address of the buffer to be filled.

Offset: 0

- <a href="#iovec.buf_len" name="iovec.buf_len"></a> `buf_len`: [`size`](#size)
  The length of the buffer to be filled.

Offset: 4

## <a href="#ciovec" name="ciovec"></a> `ciovec`: `Record`
A region of memory for scatter/gather writes.

Size: 8

Alignment: 4

### Record members
- <a href="#ciovec.buf" name="ciovec.buf"></a> `buf`: `ConstPointer<u8>`
  The address of the buffer to be written.

Offset: 0

- <a href="#ciovec.buf_len" name="ciovec.buf_len"></a> `buf_len`: [`size`](#size)
  The length of the buffer to be written.

Offset: 4

## <a href="#iovec_array" name="iovec_array"></a> `iovec_array`: `List<iovec>`

Size: 8

Alignment: 4

## <a href="#ciovec_array" name="ciovec_array"></a> `ciovec_array`: `List<ciovec>`

Size: 8

Alignment: 4

## <a href="#filedelta" name="filedelta"></a> `filedelta`: `s64`
Relative offset within a file.

Size: 8

Alignment: 8

## <a href="#whence" name="whence"></a> `whence`: `Variant`
The position relative to which to set the offset of the file descriptor.

Size: 1

Alignment: 1

### Variant cases
- <a href="#whence.set" name="whence.set"></a> `set`
  Seek relative to start-of-file.

- <a href="#whence.cur" name="whence.cur"></a> `cur`
  Seek relative to current position.

- <a href="#whence.end" name="whence.end"></a> `end`
  Seek relative to end-of-file.

## <a href="#dircookie" name="dircookie"></a> `dircookie`: `u64`
A reference to the offset of a directory entry.

The value 0 signifies the start of the directory.

Size: 8

Alignment: 8

## <a href="#dirnamlen" name="dirnamlen"></a> `dirnamlen`: `u32`
The type for the [`dirent::d_namlen`](#dirent.d_namlen) field of [`dirent`](#dirent) struct.

Size: 4

Alignment: 4

## <a href="#inode" name="inode"></a> `inode`: `u64`
File serial number that is unique within its file system.

Size: 8

Alignment: 8

## <a href="#filetype" name="filetype"></a> `filetype`: `Variant`
The type of a file descriptor or file.

Size: 1

Alignment: 1

### Variant cases
- <a href="#filetype.unknown" name="filetype.unknown"></a> `unknown`
  The type of the file descriptor or file is unknown or is different from any of the other types specified.

- <a href="#filetype.block_device" name="filetype.block_device"></a> `block_device`
  The file descriptor or file refers to a block device inode.

- <a href="#filetype.character_device" name="filetype.character_device"></a> `character_device`
  The file descriptor or file refers to a character device inode.

- <a href="#filetype.directory" name="filetype.directory"></a> `directory`
  The file descriptor or file refers to a directory inode.

- <a href="#filetype.regular_file" name="filetype.regular_file"></a> `regular_file`
  The file descriptor or file refers to a regular file inode.

- <a href="#filetype.socket_dgram" name="filetype.socket_dgram"></a> `socket_dgram`
  The file descriptor or file refers to a datagram socket.

- <a href="#filetype.socket_stream" name="filetype.socket_stream"></a> `socket_stream`
  The file descriptor or file refers to a byte-stream socket.

- <a href="#filetype.symbolic_link" name="filetype.symbolic_link"></a> `symbolic_link`
  The file refers to a symbolic link inode.

## <a href="#dirent" name="dirent"></a> `dirent`: `Record`
A directory entry.

Size: 24

Alignment: 8

### Record members
- <a href="#dirent.d_next" name="dirent.d_next"></a> `d_next`: [`dircookie`](#dircookie)
  The offset of the next directory entry stored in this directory.

Offset: 0

- <a href="#dirent.d_ino" name="dirent.d_ino"></a> `d_ino`: [`inode`](#inode)
  The serial number of the file referred to by this directory entry.

Offset: 8

- <a href="#dirent.d_namlen" name="dirent.d_namlen"></a> `d_namlen`: [`dirnamlen`](#dirnamlen)
  The length of the name of the directory entry.

Offset: 16

- <a href="#dirent.d_type" name="dirent.d_type"></a> `d_type`: [`filetype`](#filetype)
  The type of the file referred to by this directory entry.

Offset: 20

## <a href="#advice" name="advice"></a> `advice`: `Variant`
File or memory access pattern advisory information.

Size: 1

Alignment: 1

### Variant cases
- <a href="#advice.normal" name="advice.normal"></a> `normal`
  The application has no advice to give on its behavior with respect to the specified data.

- <a href="#advice.sequential" name="advice.sequential"></a> `sequential`
  The application expects to access the specified data sequentially from lower offsets to higher offsets.

- <a href="#advice.random" name="advice.random"></a> `random`
  The application expects to access the specified data in a random order.

- <a href="#advice.willneed" name="advice.willneed"></a> `willneed`
  The application expects to access the specified data in the near future.

- <a href="#advice.dontneed" name="advice.dontneed"></a> `dontneed`
  The application expects that it will not access the specified data in the near future.

- <a href="#advice.noreuse" name="advice.noreuse"></a> `noreuse`
  The application expects to access the specified data once and then not reuse it thereafter.

## <a href="#fdflags" name="fdflags"></a> `fdflags`: `Record`
File descriptor flags.

Size: 2

Alignment: 2

### Record members
- <a href="#fdflags.append" name="fdflags.append"></a> `append`: `bool`
  Append mode: Data written to the file is always appended to the file's end.

Bit: 0

- <a href="#fdflags.dsync" name="fdflags.dsync"></a> `dsync`: `bool`
  Write according to synchronized I/O data integrity completion. Only the data stored in the file is synchronized.

Bit: 1

- <a href="#fdflags.nonblock" name="fdflags.nonblock"></a> `nonblock`: `bool`
  Non-blocking mode.

Bit: 2

- <a href="#fdflags.rsync" name="fdflags.rsync"></a> `rsync`: `bool`
  Synchronized read I/O operations.

Bit: 3

- <a href="#fdflags.sync" name="fdflags.sync"></a> `sync`: `bool`
  Write according to synchronized I/O file integrity completion. In
  addition to synchronizing the data stored in the file, the implementation
  may also synchronously update the file's metadata.

Bit: 4

## <a href="#fdstat" name="fdstat"></a> `fdstat`: `Record`
File descriptor attributes.

Size: 24

Alignment: 8

### Record members
- <a href="#fdstat.fs_filetype" name="fdstat.fs_filetype"></a> `fs_filetype`: [`filetype`](#filetype)
  File type.

Offset: 0

- <a href="#fdstat.fs_flags" name="fdstat.fs_flags"></a> `fs_flags`: [`fdflags`](#fdflags)
  File descriptor flags.

Offset: 2

- <a href="#fdstat.fs_rights_base" name="fdstat.fs_rights_base"></a> `fs_rights_base`: [`rights`](#rights)
  Rights that apply to this file descriptor.

Offset: 8

- <a href="#fdstat.fs_rights_inheriting" name="fdstat.fs_rights_inheriting"></a> `fs_rights_inheriting`: [`rights`](#rights)
  Maximum set of rights that may be installed on new file descriptors that
  are created through this file descriptor, e.g., through [`path_open`](#path_open).

Offset: 16

## <a href="#device" name="device"></a> `device`: `u64`
Identifier for a device containing a file system. Can be used in combination
with [`inode`](#inode) to uniquely identify a file or directory in the filesystem.

Size: 8

Alignment: 8

## <a href="#fstflags" name="fstflags"></a> `fstflags`: `Record`
Which file time attributes to adjust.

Size: 2

Alignment: 2

### Record members
- <a href="#fstflags.atim" name="fstflags.atim"></a> `atim`: `bool`
  Adjust the last data access timestamp to the value stored in [`filestat::atim`](#filestat.atim).

Bit: 0

- <a href="#fstflags.atim_now" name="fstflags.atim_now"></a> `atim_now`: `bool`
  Adjust the last data access timestamp to the time of clock [`clockid::realtime`](#clockid.realtime).

Bit: 1

- <a href="#fstflags.mtim" name="fstflags.mtim"></a> `mtim`: `bool`
  Adjust the last data modification timestamp to the value stored in [`filestat::mtim`](#filestat.mtim).

Bit: 2

- <a href="#fstflags.mtim_now" name="fstflags.mtim_now"></a> `mtim_now`: `bool`
  Adjust the last data modification timestamp to the time of clock [`clockid::realtime`](#clockid.realtime).

Bit: 3

## <a href="#lookupflags" name="lookupflags"></a> `lookupflags`: `Record`
Flags determining the method of how paths are resolved.

Size: 4

Alignment: 4

### Record members
- <a href="#lookupflags.symlink_follow" name="lookupflags.symlink_follow"></a> `symlink_follow`: `bool`
  As long as the resolved path corresponds to a symbolic link, it is expanded.

Bit: 0

## <a href="#oflags" name="oflags"></a> `oflags`: `Record`
Open flags used by [`path_open`](#path_open).

Size: 2

Alignment: 2

### Record members
- <a href="#oflags.creat" name="oflags.creat"></a> `creat`: `bool`
  Create file if it does not exist.

Bit: 0

- <a href="#oflags.directory" name="oflags.directory"></a> `directory`: `bool`
  Fail if not a directory.

Bit: 1

- <a href="#oflags.excl" name="oflags.excl"></a> `excl`: `bool`
  Fail if file already exists.

Bit: 2

- <a href="#oflags.trunc" name="oflags.trunc"></a> `trunc`: `bool`
  Truncate file to size 0.

Bit: 3

## <a href="#linkcount" name="linkcount"></a> `linkcount`: `u64`
Number of hard links to an inode.

Size: 8

Alignment: 8

## <a href="#filestat" name="filestat"></a> `filestat`: `Record`
File attributes.

Size: 64

Alignment: 8

### Record members
- <a href="#filestat.dev" name="filestat.dev"></a> `dev`: [`device`](#device)
  Device ID of device containing the file.

Offset: 0

- <a href="#filestat.ino" name="filestat.ino"></a> `ino`: [`inode`](#inode)
  File serial number.

Offset: 8

- <a href="#filestat.filetype" name="filestat.filetype"></a> `filetype`: [`filetype`](#filetype)
  File type.

Offset: 16

- <a href="#filestat.nlink" name="filestat.nlink"></a> `nlink`: [`linkcount`](#linkcount)
  Number of hard links to the file.

Offset: 24

- <a href="#filestat.size" name="filestat.size"></a> `size`: [`filesize`](#filesize)
  For regular files, the file size in bytes. For symbolic links, the length in bytes of the pathname contained in the symbolic link.

Offset: 32

- <a href="#filestat.atim" name="filestat.atim"></a> `atim`: [`timestamp`](#timestamp)
  Last data access timestamp.

Offset: 40

- <a href="#filestat.mtim" name="filestat.mtim"></a> `mtim`: [`timestamp`](#timestamp)
  Last data modification timestamp.

Offset: 48

- <a href="#filestat.ctim" name="filestat.ctim"></a> `ctim`: [`timestamp`](#timestamp)
  Last file status change timestamp.

Offset: 56

## <a href="#userdata" name="userdata"></a> `userdata`: `u64`
User-provided value that may be attached to objects that is retained when
extracted from the implementation.

Size: 8

Alignment: 8

## <a href="#eventtype" name="eventtype"></a> `eventtype`: `Variant`
Type of a subscription to an event or its occurrence.

Size: 1

Alignment: 1

### Variant cases
- <a href="#eventtype.clock" name="eventtype.clock"></a> `clock`
  The time value of clock [`subscription_clock::id`](#subscription_clock.id) has
  reached timestamp [`subscription_clock::timeout`](#subscription_clock.timeout).

- <a href="#eventtype.fd_read" name="eventtype.fd_read"></a> `fd_read`
  File descriptor [`subscription_fd_readwrite::file_descriptor`](#subscription_fd_readwrite.file_descriptor) has data
  available for reading. This event always triggers for regular files.

- <a href="#eventtype.fd_write" name="eventtype.fd_write"></a> `fd_write`
  File descriptor [`subscription_fd_readwrite::file_descriptor`](#subscription_fd_readwrite.file_descriptor) has capacity
  available for writing. This event always triggers for regular files.

## <a href="#eventrwflags" name="eventrwflags"></a> `eventrwflags`: `Record`
The state of the file descriptor subscribed to with
[`eventtype::fd_read`](#eventtype.fd_read) or [`eventtype::fd_write`](#eventtype.fd_write).

Size: 2

Alignment: 2

### Record members
- <a href="#eventrwflags.fd_readwrite_hangup" name="eventrwflags.fd_readwrite_hangup"></a> `fd_readwrite_hangup`: `bool`
  The peer of this socket has closed or disconnected.

Bit: 0

## <a href="#event_fd_readwrite" name="event_fd_readwrite"></a> `event_fd_readwrite`: `Record`
The contents of an [`event`](#event) when type is [`eventtype::fd_read`](#eventtype.fd_read) or
[`eventtype::fd_write`](#eventtype.fd_write).

Size: 16

Alignment: 8

### Record members
- <a href="#event_fd_readwrite.nbytes" name="event_fd_readwrite.nbytes"></a> `nbytes`: [`filesize`](#filesize)
  The number of bytes available for reading or writing.

Offset: 0

- <a href="#event_fd_readwrite.flags" name="event_fd_readwrite.flags"></a> `flags`: [`eventrwflags`](#eventrwflags)
  The state of the file descriptor.

Offset: 8

## <a href="#event" name="event"></a> `event`: `Record`
An event that occurred.

Size: 32

Alignment: 8

### Record members
- <a href="#event.userdata" name="event.userdata"></a> `userdata`: [`userdata`](#userdata)
  User-provided value that got attached to [`subscription::userdata`](#subscription.userdata).

Offset: 0

- <a href="#event.error" name="event.error"></a> `error`: [`errno`](#errno)
  If non-zero, an error that occurred while processing the subscription request.

Offset: 8

- <a href="#event.type" name="event.type"></a> `type`: [`eventtype`](#eventtype)
  The type of event that occured

Offset: 10

- <a href="#event.fd_readwrite" name="event.fd_readwrite"></a> `fd_readwrite`: [`event_fd_readwrite`](#event_fd_readwrite)
  The contents of the event, if it is an [`eventtype::fd_read`](#eventtype.fd_read) or
  [`eventtype::fd_write`](#eventtype.fd_write). [`eventtype::clock`](#eventtype.clock) events ignore this field.

Offset: 16

## <a href="#subclockflags" name="subclockflags"></a> `subclockflags`: `Record`
Flags determining how to interpret the timestamp provided in
[`subscription_clock::timeout`](#subscription_clock.timeout).

Size: 2

Alignment: 2

### Record members
- <a href="#subclockflags.subscription_clock_abstime" name="subclockflags.subscription_clock_abstime"></a> `subscription_clock_abstime`: `bool`
  If set, treat the timestamp provided in
  [`subscription_clock::timeout`](#subscription_clock.timeout) as an absolute timestamp of clock
  [`subscription_clock::id`](#subscription_clock.id). If clear, treat the timestamp
  provided in [`subscription_clock::timeout`](#subscription_clock.timeout) relative to the
  current time value of clock [`subscription_clock::id`](#subscription_clock.id).

Bit: 0

## <a href="#subscription_clock" name="subscription_clock"></a> `subscription_clock`: `Record`
The contents of a [`subscription`](#subscription) when type is [`eventtype::clock`](#eventtype.clock).

Size: 32

Alignment: 8

### Record members
- <a href="#subscription_clock.id" name="subscription_clock.id"></a> `id`: [`clockid`](#clockid)
  The clock against which to compare the timestamp.

Offset: 0

- <a href="#subscription_clock.timeout" name="subscription_clock.timeout"></a> `timeout`: [`timestamp`](#timestamp)
  The absolute or relative timestamp.

Offset: 8

- <a href="#subscription_clock.precision" name="subscription_clock.precision"></a> `precision`: [`timestamp`](#timestamp)
  The amount of time that the implementation may wait additionally
  to coalesce with other events.

Offset: 16

- <a href="#subscription_clock.flags" name="subscription_clock.flags"></a> `flags`: [`subclockflags`](#subclockflags)
  Flags specifying whether the timeout is absolute or relative

Offset: 24

## <a href="#subscription_fd_readwrite" name="subscription_fd_readwrite"></a> `subscription_fd_readwrite`: `Record`
The contents of a [`subscription`](#subscription) when type is type is
[`eventtype::fd_read`](#eventtype.fd_read) or [`eventtype::fd_write`](#eventtype.fd_write).

Size: 4

Alignment: 4

### Record members
- <a href="#subscription_fd_readwrite.file_descriptor" name="subscription_fd_readwrite.file_descriptor"></a> `file_descriptor`: [`fd`](#fd)
  The file descriptor on which to wait for it to become ready for reading or writing.

Offset: 0

## <a href="#subscription_u" name="subscription_u"></a> `subscription_u`: `Variant`
The contents of a [`subscription`](#subscription).

Size: 40

Alignment: 8

### Variant Layout
- size: 40
- align: 8
- tag_size: 1
### Variant cases
- <a href="#subscription_u.clock" name="subscription_u.clock"></a> `clock`: [`subscription_clock`](#subscription_clock)

- <a href="#subscription_u.fd_read" name="subscription_u.fd_read"></a> `fd_read`: [`subscription_fd_readwrite`](#subscription_fd_readwrite)

- <a href="#subscription_u.fd_write" name="subscription_u.fd_write"></a> `fd_write`: [`subscription_fd_readwrite`](#subscription_fd_readwrite)

## <a href="#subscription" name="subscription"></a> `subscription`: `Record`
Subscription to an event.

Size: 48

Alignment: 8

### Record members
- <a href="#subscription.userdata" name="subscription.userdata"></a> `userdata`: [`userdata`](#userdata)
  User-provided value that is attached to the subscription in the
  implementation and returned through [`event::userdata`](#event.userdata).

Offset: 0

- <a href="#subscription.u" name="subscription.u"></a> `u`: [`subscription_u`](#subscription_u)
  The type of the event to which to subscribe, and its contents

Offset: 8

## <a href="#exitcode" name="exitcode"></a> `exitcode`: `u32`
Exit code generated by a process when exiting.

Size: 4

Alignment: 4

## <a href="#signal" name="signal"></a> `signal`: `Variant`
Signal condition.

Size: 1

Alignment: 1

### Variant cases
- <a href="#signal.none" name="signal.none"></a> `none`
  No signal. Note that POSIX has special semantics for `kill(pid, 0)`,
  so this value is reserved.

- <a href="#signal.hup" name="signal.hup"></a> `hup`
  Hangup.
  Action: Terminates the process.

- <a href="#signal.int" name="signal.int"></a> `int`
  Terminate interrupt signal.
  Action: Terminates the process.

- <a href="#signal.quit" name="signal.quit"></a> `quit`
  Terminal quit signal.
  Action: Terminates the process.

- <a href="#signal.ill" name="signal.ill"></a> `ill`
  Illegal instruction.
  Action: Terminates the process.

- <a href="#signal.trap" name="signal.trap"></a> `trap`
  Trace/breakpoint trap.
  Action: Terminates the process.

- <a href="#signal.abrt" name="signal.abrt"></a> `abrt`
  Process abort signal.
  Action: Terminates the process.

- <a href="#signal.bus" name="signal.bus"></a> `bus`
  Access to an undefined portion of a memory object.
  Action: Terminates the process.

- <a href="#signal.fpe" name="signal.fpe"></a> `fpe`
  Erroneous arithmetic operation.
  Action: Terminates the process.

- <a href="#signal.kill" name="signal.kill"></a> `kill`
  Kill.
  Action: Terminates the process.

- <a href="#signal.usr1" name="signal.usr1"></a> `usr1`
  User-defined signal 1.
  Action: Terminates the process.

- <a href="#signal.segv" name="signal.segv"></a> `segv`
  Invalid memory reference.
  Action: Terminates the process.

- <a href="#signal.usr2" name="signal.usr2"></a> `usr2`
  User-defined signal 2.
  Action: Terminates the process.

- <a href="#signal.pipe" name="signal.pipe"></a> `pipe`
  Write on a pipe with no one to read it.
  Action: Ignored.

- <a href="#signal.alrm" name="signal.alrm"></a> `alrm`
  Alarm clock.
  Action: Terminates the process.

- <a href="#signal.term" name="signal.term"></a> `term`
  Termination signal.
  Action: Terminates the process.

- <a href="#signal.chld" name="signal.chld"></a> `chld`
  Child process terminated, stopped, or continued.
  Action: Ignored.

- <a href="#signal.cont" name="signal.cont"></a> `cont`
  Continue executing, if stopped.
  Action: Continues executing, if stopped.

- <a href="#signal.stop" name="signal.stop"></a> `stop`
  Stop executing.
  Action: Stops executing.

- <a href="#signal.tstp" name="signal.tstp"></a> `tstp`
  Terminal stop signal.
  Action: Stops executing.

- <a href="#signal.ttin" name="signal.ttin"></a> `ttin`
  Background process attempting read.
  Action: Stops executing.

- <a href="#signal.ttou" name="signal.ttou"></a> `ttou`
  Background process attempting write.
  Action: Stops executing.

- <a href="#signal.urg" name="signal.urg"></a> `urg`
  High bandwidth data is available at a socket.
  Action: Ignored.

- <a href="#signal.xcpu" name="signal.xcpu"></a> `xcpu`
  CPU time limit exceeded.
  Action: Terminates the process.

- <a href="#signal.xfsz" name="signal.xfsz"></a> `xfsz`
  File size limit exceeded.
  Action: Terminates the process.

- <a href="#signal.vtalrm" name="signal.vtalrm"></a> `vtalrm`
  Virtual timer expired.
  Action: Terminates the process.

- <a href="#signal.prof" name="signal.prof"></a> `prof`
  Profiling timer expired.
  Action: Terminates the process.

- <a href="#signal.winch" name="signal.winch"></a> `winch`
  Window changed.
  Action: Ignored.

- <a href="#signal.poll" name="signal.poll"></a> `poll`
  I/O possible.
  Action: Terminates the process.

- <a href="#signal.pwr" name="signal.pwr"></a> `pwr`
  Power failure.
  Action: Terminates the process.

- <a href="#signal.sys" name="signal.sys"></a> `sys`
  Bad system call.
  Action: Terminates the process.

## <a href="#riflags" name="riflags"></a> `riflags`: `Record`
Flags provided to [`sock_recv`](#sock_recv).

Size: 2

Alignment: 2

### Record members
- <a href="#riflags.recv_peek" name="riflags.recv_peek"></a> `recv_peek`: `bool`
  Returns the message without removing it from the socket's receive queue.

Bit: 0

- <a href="#riflags.recv_waitall" name="riflags.recv_waitall"></a> `recv_waitall`: `bool`
  On byte-stream sockets, block until the full amount of data can be returned.

Bit: 1

## <a href="#roflags" name="roflags"></a> `roflags`: `Record`
Flags returned by [`sock_recv`](#sock_recv).

Size: 2

Alignment: 2

### Record members
- <a href="#roflags.recv_data_truncated" name="roflags.recv_data_truncated"></a> `recv_data_truncated`: `bool`
  Returned by [`sock_recv`](#sock_recv): Message data has been truncated.

Bit: 0

## <a href="#siflags" name="siflags"></a> `siflags`: `u16`
Flags provided to [`sock_send`](#sock_send). As there are currently no flags
defined, it must be set to zero.

Size: 2

Alignment: 2

## <a href="#sdflags" name="sdflags"></a> `sdflags`: `Record`
Which channels on a socket to shut down.

Size: 1

Alignment: 1

### Record members
- <a href="#sdflags.rd" name="sdflags.rd"></a> `rd`: `bool`
  Disables further receive operations.

Bit: 0

- <a href="#sdflags.wr" name="sdflags.wr"></a> `wr`: `bool`
  Disables further send operations.

Bit: 1

## <a href="#preopentype" name="preopentype"></a> `preopentype`: `Variant`
Identifiers for preopened capabilities.

Size: 1

Alignment: 1

### Variant cases
- <a href="#preopentype.dir" name="preopentype.dir"></a> `dir`
  A pre-opened directory.

## <a href="#prestat_dir" name="prestat_dir"></a> `prestat_dir`: `Record`
The contents of a $prestat when type is [`preopentype::dir`](#preopentype.dir).

Size: 4

Alignment: 4

### Record members
- <a href="#prestat_dir.pr_name_len" name="prestat_dir.pr_name_len"></a> `pr_name_len`: [`size`](#size)
  The length of the directory name for use with [`fd_prestat_dir_name`](#fd_prestat_dir_name).

Offset: 0

## <a href="#prestat" name="prestat"></a> `prestat`: `Variant`
Information about a pre-opened capability.

Size: 8

Alignment: 4

### Variant Layout
- size: 8
- align: 4
- tag_size: 1
### Variant cases
- <a href="#prestat.dir" name="prestat.dir"></a> `dir`: [`prestat_dir`](#prestat_dir)

# Modules
## <a href="#wasi_snapshot_preview1" name="wasi_snapshot_preview1"></a> wasi_snapshot_preview1
### Imports
#### Memory
### Functions

---

#### <a href="#args_get" name="args_get"></a> `args_get(argv: Pointer<Pointer<u8>>, argv_buf: Pointer<u8>) -> Result<(), errno>`
Read command-line argument data.
The size of the array should match that returned by [`args_sizes_get`](#args_sizes_get).
Each argument is expected to be `\0` terminated.

##### Params
- <a href="#args_get.argv" name="args_get.argv"></a> `argv`: `Pointer<Pointer<u8>>`

- <a href="#args_get.argv_buf" name="args_get.argv_buf"></a> `argv_buf`: `Pointer<u8>`

##### Results
- <a href="#args_get.error" name="args_get.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#args_get.error.ok" name="args_get.error.ok"></a> `ok`

- <a href="#args_get.error.err" name="args_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#args_sizes_get" name="args_sizes_get"></a> `args_sizes_get() -> Result<(size, size), errno>`
Return command-line argument data sizes.

##### Params
##### Results
- <a href="#args_sizes_get.error" name="args_sizes_get.error"></a> `error`: `Result<(size, size), errno>`
  Returns the number of arguments and the size of the argument string
  data, or an error.

###### Variant Layout
- size: 12
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#args_sizes_get.error.ok" name="args_sizes_get.error.ok"></a> `ok`: `(size, size)`

####### Record members
- <a href="#args_sizes_get.error.ok.0" name="args_sizes_get.error.ok.0"></a> `0`: [`size`](#size)

Offset: 0

- <a href="#args_sizes_get.error.ok.1" name="args_sizes_get.error.ok.1"></a> `1`: [`size`](#size)

Offset: 4

- <a href="#args_sizes_get.error.err" name="args_sizes_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#environ_get" name="environ_get"></a> `environ_get(environ: Pointer<Pointer<u8>>, environ_buf: Pointer<u8>) -> Result<(), errno>`
Read environment variable data.
The sizes of the buffers should match that returned by [`environ_sizes_get`](#environ_sizes_get).
Key/value pairs are expected to be joined with `=`s, and terminated with `\0`s.

##### Params
- <a href="#environ_get.environ" name="environ_get.environ"></a> `environ`: `Pointer<Pointer<u8>>`

- <a href="#environ_get.environ_buf" name="environ_get.environ_buf"></a> `environ_buf`: `Pointer<u8>`

##### Results
- <a href="#environ_get.error" name="environ_get.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#environ_get.error.ok" name="environ_get.error.ok"></a> `ok`

- <a href="#environ_get.error.err" name="environ_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#environ_sizes_get" name="environ_sizes_get"></a> `environ_sizes_get() -> Result<(size, size), errno>`
Return environment variable data sizes.

##### Params
##### Results
- <a href="#environ_sizes_get.error" name="environ_sizes_get.error"></a> `error`: `Result<(size, size), errno>`
  Returns the number of environment variable arguments and the size of the
  environment variable data.

###### Variant Layout
- size: 12
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#environ_sizes_get.error.ok" name="environ_sizes_get.error.ok"></a> `ok`: `(size, size)`

####### Record members
- <a href="#environ_sizes_get.error.ok.0" name="environ_sizes_get.error.ok.0"></a> `0`: [`size`](#size)

Offset: 0

- <a href="#environ_sizes_get.error.ok.1" name="environ_sizes_get.error.ok.1"></a> `1`: [`size`](#size)

Offset: 4

- <a href="#environ_sizes_get.error.err" name="environ_sizes_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#clock_res_get" name="clock_res_get"></a> `clock_res_get(id: clockid) -> Result<timestamp, errno>`
Return the resolution of a clock.
Implementations are required to provide a non-zero value for supported clocks. For unsupported clocks,
return [`errno::inval`](#errno.inval).
Note: This is similar to `clock_getres` in POSIX.

##### Params
- <a href="#clock_res_get.id" name="clock_res_get.id"></a> `id`: [`clockid`](#clockid)
  The clock for which to return the resolution.

##### Results
- <a href="#clock_res_get.error" name="clock_res_get.error"></a> `error`: `Result<timestamp, errno>`
  The resolution of the clock, or an error if one happened.

###### Variant Layout
- size: 16
- align: 8
- tag_size: 4
###### Variant cases
- <a href="#clock_res_get.error.ok" name="clock_res_get.error.ok"></a> `ok`: [`timestamp`](#timestamp)

- <a href="#clock_res_get.error.err" name="clock_res_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#clock_time_get" name="clock_time_get"></a> `clock_time_get(id: clockid, precision: timestamp) -> Result<timestamp, errno>`
Return the time value of a clock.
Note: This is similar to `clock_gettime` in POSIX.

##### Params
- <a href="#clock_time_get.id" name="clock_time_get.id"></a> `id`: [`clockid`](#clockid)
  The clock for which to return the time.

- <a href="#clock_time_get.precision" name="clock_time_get.precision"></a> `precision`: [`timestamp`](#timestamp)
  The maximum lag (exclusive) that the returned time value may have, compared to its actual value.

##### Results
- <a href="#clock_time_get.error" name="clock_time_get.error"></a> `error`: `Result<timestamp, errno>`
  The time value of the clock.

###### Variant Layout
- size: 16
- align: 8
- tag_size: 4
###### Variant cases
- <a href="#clock_time_get.error.ok" name="clock_time_get.error.ok"></a> `ok`: [`timestamp`](#timestamp)

- <a href="#clock_time_get.error.err" name="clock_time_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_advise" name="fd_advise"></a> `fd_advise(fd: fd, offset: filesize, len: filesize, advice: advice) -> Result<(), errno>`
Provide file advisory information on a file descriptor.
Note: This is similar to `posix_fadvise` in POSIX.

##### Params
- <a href="#fd_advise.fd" name="fd_advise.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_advise.offset" name="fd_advise.offset"></a> `offset`: [`filesize`](#filesize)
  The offset within the file to which the advisory applies.

- <a href="#fd_advise.len" name="fd_advise.len"></a> `len`: [`filesize`](#filesize)
  The length of the region to which the advisory applies.

- <a href="#fd_advise.advice" name="fd_advise.advice"></a> `advice`: [`advice`](#advice)
  The advice.

##### Results
- <a href="#fd_advise.error" name="fd_advise.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_advise.error.ok" name="fd_advise.error.ok"></a> `ok`

- <a href="#fd_advise.error.err" name="fd_advise.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_allocate" name="fd_allocate"></a> `fd_allocate(fd: fd, offset: filesize, len: filesize) -> Result<(), errno>`
Force the allocation of space in a file.
Note: This is similar to `posix_fallocate` in POSIX.

##### Params
- <a href="#fd_allocate.fd" name="fd_allocate.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_allocate.offset" name="fd_allocate.offset"></a> `offset`: [`filesize`](#filesize)
  The offset at which to start the allocation.

- <a href="#fd_allocate.len" name="fd_allocate.len"></a> `len`: [`filesize`](#filesize)
  The length of the area that is allocated.

##### Results
- <a href="#fd_allocate.error" name="fd_allocate.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_allocate.error.ok" name="fd_allocate.error.ok"></a> `ok`

- <a href="#fd_allocate.error.err" name="fd_allocate.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_close" name="fd_close"></a> `fd_close(fd: fd) -> Result<(), errno>`
Close a file descriptor.
Note: This is similar to `close` in POSIX.

##### Params
- <a href="#fd_close.fd" name="fd_close.fd"></a> `fd`: [`fd`](#fd)

##### Results
- <a href="#fd_close.error" name="fd_close.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_close.error.ok" name="fd_close.error.ok"></a> `ok`

- <a href="#fd_close.error.err" name="fd_close.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_datasync" name="fd_datasync"></a> `fd_datasync(fd: fd) -> Result<(), errno>`
Synchronize the data of a file to disk.
Note: This is similar to `fdatasync` in POSIX.

##### Params
- <a href="#fd_datasync.fd" name="fd_datasync.fd"></a> `fd`: [`fd`](#fd)

##### Results
- <a href="#fd_datasync.error" name="fd_datasync.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_datasync.error.ok" name="fd_datasync.error.ok"></a> `ok`

- <a href="#fd_datasync.error.err" name="fd_datasync.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_fdstat_get" name="fd_fdstat_get"></a> `fd_fdstat_get(fd: fd) -> Result<fdstat, errno>`
Get the attributes of a file descriptor.
Note: This returns similar flags to `fcntl(fd, F_GETFL)` in POSIX, as well as additional fields.

##### Params
- <a href="#fd_fdstat_get.fd" name="fd_fdstat_get.fd"></a> `fd`: [`fd`](#fd)

##### Results
- <a href="#fd_fdstat_get.error" name="fd_fdstat_get.error"></a> `error`: `Result<fdstat, errno>`
  The buffer where the file descriptor's attributes are stored.

###### Variant Layout
- size: 32
- align: 8
- tag_size: 4
###### Variant cases
- <a href="#fd_fdstat_get.error.ok" name="fd_fdstat_get.error.ok"></a> `ok`: [`fdstat`](#fdstat)

- <a href="#fd_fdstat_get.error.err" name="fd_fdstat_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_fdstat_set_flags" name="fd_fdstat_set_flags"></a> `fd_fdstat_set_flags(fd: fd, flags: fdflags) -> Result<(), errno>`
Adjust the flags associated with a file descriptor.
Note: This is similar to `fcntl(fd, F_SETFL, flags)` in POSIX.

##### Params
- <a href="#fd_fdstat_set_flags.fd" name="fd_fdstat_set_flags.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_fdstat_set_flags.flags" name="fd_fdstat_set_flags.flags"></a> `flags`: [`fdflags`](#fdflags)
  The desired values of the file descriptor flags.

##### Results
- <a href="#fd_fdstat_set_flags.error" name="fd_fdstat_set_flags.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_fdstat_set_flags.error.ok" name="fd_fdstat_set_flags.error.ok"></a> `ok`

- <a href="#fd_fdstat_set_flags.error.err" name="fd_fdstat_set_flags.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_fdstat_set_rights" name="fd_fdstat_set_rights"></a> `fd_fdstat_set_rights(fd: fd, fs_rights_base: rights, fs_rights_inheriting: rights) -> Result<(), errno>`
Adjust the rights associated with a file descriptor.
This can only be used to remove rights, and returns [`errno::notcapable`](#errno.notcapable) if called in a way that would attempt to add rights

##### Params
- <a href="#fd_fdstat_set_rights.fd" name="fd_fdstat_set_rights.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_fdstat_set_rights.fs_rights_base" name="fd_fdstat_set_rights.fs_rights_base"></a> `fs_rights_base`: [`rights`](#rights)
  The desired rights of the file descriptor.

- <a href="#fd_fdstat_set_rights.fs_rights_inheriting" name="fd_fdstat_set_rights.fs_rights_inheriting"></a> `fs_rights_inheriting`: [`rights`](#rights)

##### Results
- <a href="#fd_fdstat_set_rights.error" name="fd_fdstat_set_rights.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_fdstat_set_rights.error.ok" name="fd_fdstat_set_rights.error.ok"></a> `ok`

- <a href="#fd_fdstat_set_rights.error.err" name="fd_fdstat_set_rights.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_filestat_get" name="fd_filestat_get"></a> `fd_filestat_get(fd: fd) -> Result<filestat, errno>`
Return the attributes of an open file.

##### Params
- <a href="#fd_filestat_get.fd" name="fd_filestat_get.fd"></a> `fd`: [`fd`](#fd)

##### Results
- <a href="#fd_filestat_get.error" name="fd_filestat_get.error"></a> `error`: `Result<filestat, errno>`
  The buffer where the file's attributes are stored.

###### Variant Layout
- size: 72
- align: 8
- tag_size: 4
###### Variant cases
- <a href="#fd_filestat_get.error.ok" name="fd_filestat_get.error.ok"></a> `ok`: [`filestat`](#filestat)

- <a href="#fd_filestat_get.error.err" name="fd_filestat_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_filestat_set_size" name="fd_filestat_set_size"></a> `fd_filestat_set_size(fd: fd, size: filesize) -> Result<(), errno>`
Adjust the size of an open file. If this increases the file's size, the extra bytes are filled with zeros.
Note: This is similar to `ftruncate` in POSIX.

##### Params
- <a href="#fd_filestat_set_size.fd" name="fd_filestat_set_size.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_filestat_set_size.size" name="fd_filestat_set_size.size"></a> `size`: [`filesize`](#filesize)
  The desired file size.

##### Results
- <a href="#fd_filestat_set_size.error" name="fd_filestat_set_size.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_filestat_set_size.error.ok" name="fd_filestat_set_size.error.ok"></a> `ok`

- <a href="#fd_filestat_set_size.error.err" name="fd_filestat_set_size.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_filestat_set_times" name="fd_filestat_set_times"></a> `fd_filestat_set_times(fd: fd, atim: timestamp, mtim: timestamp, fst_flags: fstflags) -> Result<(), errno>`
Adjust the timestamps of an open file or directory.
Note: This is similar to `futimens` in POSIX.

##### Params
- <a href="#fd_filestat_set_times.fd" name="fd_filestat_set_times.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_filestat_set_times.atim" name="fd_filestat_set_times.atim"></a> `atim`: [`timestamp`](#timestamp)
  The desired values of the data access timestamp.

- <a href="#fd_filestat_set_times.mtim" name="fd_filestat_set_times.mtim"></a> `mtim`: [`timestamp`](#timestamp)
  The desired values of the data modification timestamp.

- <a href="#fd_filestat_set_times.fst_flags" name="fd_filestat_set_times.fst_flags"></a> `fst_flags`: [`fstflags`](#fstflags)
  A bitmask indicating which timestamps to adjust.

##### Results
- <a href="#fd_filestat_set_times.error" name="fd_filestat_set_times.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_filestat_set_times.error.ok" name="fd_filestat_set_times.error.ok"></a> `ok`

- <a href="#fd_filestat_set_times.error.err" name="fd_filestat_set_times.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_pread" name="fd_pread"></a> `fd_pread(fd: fd, iovs: iovec_array, offset: filesize) -> Result<size, errno>`
Read from a file descriptor, without using and updating the file descriptor's offset.
Note: This is similar to `preadv` in Linux (and other Unix-es).

##### Params
- <a href="#fd_pread.fd" name="fd_pread.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_pread.iovs" name="fd_pread.iovs"></a> `iovs`: [`iovec_array`](#iovec_array)
  List of scatter/gather vectors in which to store data.

- <a href="#fd_pread.offset" name="fd_pread.offset"></a> `offset`: [`filesize`](#filesize)
  The offset within the file at which to read.

##### Results
- <a href="#fd_pread.error" name="fd_pread.error"></a> `error`: `Result<size, errno>`
  The number of bytes read.

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_pread.error.ok" name="fd_pread.error.ok"></a> `ok`: [`size`](#size)

- <a href="#fd_pread.error.err" name="fd_pread.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_prestat_get" name="fd_prestat_get"></a> `fd_prestat_get(fd: fd) -> Result<prestat, errno>`
Return a description of the given preopened file descriptor.

##### Params
- <a href="#fd_prestat_get.fd" name="fd_prestat_get.fd"></a> `fd`: [`fd`](#fd)

##### Results
- <a href="#fd_prestat_get.error" name="fd_prestat_get.error"></a> `error`: `Result<prestat, errno>`
  The buffer where the description is stored.

###### Variant Layout
- size: 12
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_prestat_get.error.ok" name="fd_prestat_get.error.ok"></a> `ok`: [`prestat`](#prestat)

- <a href="#fd_prestat_get.error.err" name="fd_prestat_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_prestat_dir_name" name="fd_prestat_dir_name"></a> `fd_prestat_dir_name(fd: fd, path: Pointer<u8>, path_len: size) -> Result<(), errno>`
Return a description of the given preopened file descriptor.

##### Params
- <a href="#fd_prestat_dir_name.fd" name="fd_prestat_dir_name.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_prestat_dir_name.path" name="fd_prestat_dir_name.path"></a> `path`: `Pointer<u8>`
  A buffer into which to write the preopened directory name.

- <a href="#fd_prestat_dir_name.path_len" name="fd_prestat_dir_name.path_len"></a> `path_len`: [`size`](#size)

##### Results
- <a href="#fd_prestat_dir_name.error" name="fd_prestat_dir_name.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_prestat_dir_name.error.ok" name="fd_prestat_dir_name.error.ok"></a> `ok`

- <a href="#fd_prestat_dir_name.error.err" name="fd_prestat_dir_name.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_pwrite" name="fd_pwrite"></a> `fd_pwrite(fd: fd, iovs: ciovec_array, offset: filesize) -> Result<size, errno>`
Write to a file descriptor, without using and updating the file descriptor's offset.
Note: This is similar to `pwritev` in Linux (and other Unix-es).

Like Linux (and other Unix-es), any calls of `pwrite` (and other
functions to read or write) for a regular file by other threads in the
WASI process should not be interleaved while `pwrite` is executed.

##### Params
- <a href="#fd_pwrite.fd" name="fd_pwrite.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_pwrite.iovs" name="fd_pwrite.iovs"></a> `iovs`: [`ciovec_array`](#ciovec_array)
  List of scatter/gather vectors from which to retrieve data.

- <a href="#fd_pwrite.offset" name="fd_pwrite.offset"></a> `offset`: [`filesize`](#filesize)
  The offset within the file at which to write.

##### Results
- <a href="#fd_pwrite.error" name="fd_pwrite.error"></a> `error`: `Result<size, errno>`
  The number of bytes written.

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_pwrite.error.ok" name="fd_pwrite.error.ok"></a> `ok`: [`size`](#size)

- <a href="#fd_pwrite.error.err" name="fd_pwrite.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_read" name="fd_read"></a> `fd_read(fd: fd, iovs: iovec_array) -> Result<size, errno>`
Read from a file descriptor.
Note: This is similar to `readv` in POSIX.

##### Params
- <a href="#fd_read.fd" name="fd_read.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_read.iovs" name="fd_read.iovs"></a> `iovs`: [`iovec_array`](#iovec_array)
  List of scatter/gather vectors to which to store data.

##### Results
- <a href="#fd_read.error" name="fd_read.error"></a> `error`: `Result<size, errno>`
  The number of bytes read.

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_read.error.ok" name="fd_read.error.ok"></a> `ok`: [`size`](#size)

- <a href="#fd_read.error.err" name="fd_read.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_readdir" name="fd_readdir"></a> `fd_readdir(fd: fd, buf: Pointer<u8>, buf_len: size, cookie: dircookie) -> Result<size, errno>`
Read directory entries from a directory.
When successful, the contents of the output buffer consist of a sequence of
directory entries. Each directory entry consists of a [`dirent`](#dirent) object,
followed by [`dirent::d_namlen`](#dirent.d_namlen) bytes holding the name of the directory
entry.
This function fills the output buffer as much as possible, potentially
truncating the last directory entry. This allows the caller to grow its
read buffer size in case it's too small to fit a single large directory
entry, or skip the oversized directory entry.

##### Params
- <a href="#fd_readdir.fd" name="fd_readdir.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_readdir.buf" name="fd_readdir.buf"></a> `buf`: `Pointer<u8>`
  The buffer where directory entries are stored

- <a href="#fd_readdir.buf_len" name="fd_readdir.buf_len"></a> `buf_len`: [`size`](#size)

- <a href="#fd_readdir.cookie" name="fd_readdir.cookie"></a> `cookie`: [`dircookie`](#dircookie)
  The location within the directory to start reading

##### Results
- <a href="#fd_readdir.error" name="fd_readdir.error"></a> `error`: `Result<size, errno>`
  The number of bytes stored in the read buffer. If less than the size of the read buffer, the end of the directory has been reached.

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_readdir.error.ok" name="fd_readdir.error.ok"></a> `ok`: [`size`](#size)

- <a href="#fd_readdir.error.err" name="fd_readdir.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_renumber" name="fd_renumber"></a> `fd_renumber(fd: fd, to: fd) -> Result<(), errno>`
Atomically replace a file descriptor by renumbering another file descriptor.
Due to the strong focus on thread safety, this environment does not provide
a mechanism to duplicate or renumber a file descriptor to an arbitrary
number, like `dup2()`. This would be prone to race conditions, as an actual
file descriptor with the same number could be allocated by a different
thread at the same time.
This function provides a way to atomically renumber file descriptors, which
would disappear if `dup2()` were to be removed entirely.

##### Params
- <a href="#fd_renumber.fd" name="fd_renumber.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_renumber.to" name="fd_renumber.to"></a> `to`: [`fd`](#fd)
  The file descriptor to overwrite.

##### Results
- <a href="#fd_renumber.error" name="fd_renumber.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_renumber.error.ok" name="fd_renumber.error.ok"></a> `ok`

- <a href="#fd_renumber.error.err" name="fd_renumber.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_seek" name="fd_seek"></a> `fd_seek(fd: fd, offset: filedelta, whence: whence) -> Result<filesize, errno>`
Move the offset of a file descriptor.
Note: This is similar to `lseek` in POSIX.

##### Params
- <a href="#fd_seek.fd" name="fd_seek.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_seek.offset" name="fd_seek.offset"></a> `offset`: [`filedelta`](#filedelta)
  The number of bytes to move.

- <a href="#fd_seek.whence" name="fd_seek.whence"></a> `whence`: [`whence`](#whence)
  The base from which the offset is relative.

##### Results
- <a href="#fd_seek.error" name="fd_seek.error"></a> `error`: `Result<filesize, errno>`
  The new offset of the file descriptor, relative to the start of the file.

###### Variant Layout
- size: 16
- align: 8
- tag_size: 4
###### Variant cases
- <a href="#fd_seek.error.ok" name="fd_seek.error.ok"></a> `ok`: [`filesize`](#filesize)

- <a href="#fd_seek.error.err" name="fd_seek.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_sync" name="fd_sync"></a> `fd_sync(fd: fd) -> Result<(), errno>`
Synchronize the data and metadata of a file to disk.
Note: This is similar to `fsync` in POSIX.

##### Params
- <a href="#fd_sync.fd" name="fd_sync.fd"></a> `fd`: [`fd`](#fd)

##### Results
- <a href="#fd_sync.error" name="fd_sync.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_sync.error.ok" name="fd_sync.error.ok"></a> `ok`

- <a href="#fd_sync.error.err" name="fd_sync.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_tell" name="fd_tell"></a> `fd_tell(fd: fd) -> Result<filesize, errno>`
Return the current offset of a file descriptor.
Note: This is similar to `lseek(fd, 0, SEEK_CUR)` in POSIX.

##### Params
- <a href="#fd_tell.fd" name="fd_tell.fd"></a> `fd`: [`fd`](#fd)

##### Results
- <a href="#fd_tell.error" name="fd_tell.error"></a> `error`: `Result<filesize, errno>`
  The current offset of the file descriptor, relative to the start of the file.

###### Variant Layout
- size: 16
- align: 8
- tag_size: 4
###### Variant cases
- <a href="#fd_tell.error.ok" name="fd_tell.error.ok"></a> `ok`: [`filesize`](#filesize)

- <a href="#fd_tell.error.err" name="fd_tell.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#fd_write" name="fd_write"></a> `fd_write(fd: fd, iovs: ciovec_array) -> Result<size, errno>`
Write to a file descriptor.
Note: This is similar to `writev` in POSIX.

Like POSIX, any calls of `write` (and other functions to read or write)
for a regular file by other threads in the WASI process should not be
interleaved while `write` is executed.

##### Params
- <a href="#fd_write.fd" name="fd_write.fd"></a> `fd`: [`fd`](#fd)

- <a href="#fd_write.iovs" name="fd_write.iovs"></a> `iovs`: [`ciovec_array`](#ciovec_array)
  List of scatter/gather vectors from which to retrieve data.

##### Results
- <a href="#fd_write.error" name="fd_write.error"></a> `error`: `Result<size, errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#fd_write.error.ok" name="fd_write.error.ok"></a> `ok`: [`size`](#size)

- <a href="#fd_write.error.err" name="fd_write.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#path_create_directory" name="path_create_directory"></a> `path_create_directory(fd: fd, path: string) -> Result<(), errno>`
Create a directory.
Note: This is similar to `mkdirat` in POSIX.

##### Params
- <a href="#path_create_directory.fd" name="path_create_directory.fd"></a> `fd`: [`fd`](#fd)

- <a href="#path_create_directory.path" name="path_create_directory.path"></a> `path`: `string`
  The path at which to create the directory.

##### Results
- <a href="#path_create_directory.error" name="path_create_directory.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#path_create_directory.error.ok" name="path_create_directory.error.ok"></a> `ok`

- <a href="#path_create_directory.error.err" name="path_create_directory.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#path_filestat_get" name="path_filestat_get"></a> `path_filestat_get(fd: fd, flags: lookupflags, path: string) -> Result<filestat, errno>`
Return the attributes of a file or directory.
Note: This is similar to `stat` in POSIX.

##### Params
- <a href="#path_filestat_get.fd" name="path_filestat_get.fd"></a> `fd`: [`fd`](#fd)

- <a href="#path_filestat_get.flags" name="path_filestat_get.flags"></a> `flags`: [`lookupflags`](#lookupflags)
  Flags determining the method of how the path is resolved.

- <a href="#path_filestat_get.path" name="path_filestat_get.path"></a> `path`: `string`
  The path of the file or directory to inspect.

##### Results
- <a href="#path_filestat_get.error" name="path_filestat_get.error"></a> `error`: `Result<filestat, errno>`
  The buffer where the file's attributes are stored.

###### Variant Layout
- size: 72
- align: 8
- tag_size: 4
###### Variant cases
- <a href="#path_filestat_get.error.ok" name="path_filestat_get.error.ok"></a> `ok`: [`filestat`](#filestat)

- <a href="#path_filestat_get.error.err" name="path_filestat_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#path_filestat_set_times" name="path_filestat_set_times"></a> `path_filestat_set_times(fd: fd, flags: lookupflags, path: string, atim: timestamp, mtim: timestamp, fst_flags: fstflags) -> Result<(), errno>`
Adjust the timestamps of a file or directory.
Note: This is similar to `utimensat` in POSIX.

##### Params
- <a href="#path_filestat_set_times.fd" name="path_filestat_set_times.fd"></a> `fd`: [`fd`](#fd)

- <a href="#path_filestat_set_times.flags" name="path_filestat_set_times.flags"></a> `flags`: [`lookupflags`](#lookupflags)
  Flags determining the method of how the path is resolved.

- <a href="#path_filestat_set_times.path" name="path_filestat_set_times.path"></a> `path`: `string`
  The path of the file or directory to operate on.

- <a href="#path_filestat_set_times.atim" name="path_filestat_set_times.atim"></a> `atim`: [`timestamp`](#timestamp)
  The desired values of the data access timestamp.

- <a href="#path_filestat_set_times.mtim" name="path_filestat_set_times.mtim"></a> `mtim`: [`timestamp`](#timestamp)
  The desired values of the data modification timestamp.

- <a href="#path_filestat_set_times.fst_flags" name="path_filestat_set_times.fst_flags"></a> `fst_flags`: [`fstflags`](#fstflags)
  A bitmask indicating which timestamps to adjust.

##### Results
- <a href="#path_filestat_set_times.error" name="path_filestat_set_times.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#path_filestat_set_times.error.ok" name="path_filestat_set_times.error.ok"></a> `ok`

- <a href="#path_filestat_set_times.error.err" name="path_filestat_set_times.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#path_link" name="path_link"></a> `path_link(old_fd: fd, old_flags: lookupflags, old_path: string, new_fd: fd, new_path: string) -> Result<(), errno>`
Create a hard link.
Note: This is similar to `linkat` in POSIX.

##### Params
- <a href="#path_link.old_fd" name="path_link.old_fd"></a> `old_fd`: [`fd`](#fd)

- <a href="#path_link.old_flags" name="path_link.old_flags"></a> `old_flags`: [`lookupflags`](#lookupflags)
  Flags determining the method of how the path is resolved.

- <a href="#path_link.old_path" name="path_link.old_path"></a> `old_path`: `string`
  The source path from which to link.

- <a href="#path_link.new_fd" name="path_link.new_fd"></a> `new_fd`: [`fd`](#fd)
  The working directory at which the resolution of the new path starts.

- <a href="#path_link.new_path" name="path_link.new_path"></a> `new_path`: `string`
  The destination path at which to create the hard link.

##### Results
- <a href="#path_link.error" name="path_link.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#path_link.error.ok" name="path_link.error.ok"></a> `ok`

- <a href="#path_link.error.err" name="path_link.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#path_open" name="path_open"></a> `path_open(fd: fd, dirflags: lookupflags, path: string, oflags: oflags, fs_rights_base: rights, fs_rights_inheriting: rights, fdflags: fdflags) -> Result<fd, errno>`
Open a file or directory.
The returned file descriptor is not guaranteed to be the lowest-numbered
file descriptor not currently open; it is randomized to prevent
applications from depending on making assumptions about indexes, since this
is error-prone in multi-threaded contexts. The returned file descriptor is
guaranteed to be less than 2**31.
Note: This is similar to `openat` in POSIX.

##### Params
- <a href="#path_open.fd" name="path_open.fd"></a> `fd`: [`fd`](#fd)

- <a href="#path_open.dirflags" name="path_open.dirflags"></a> `dirflags`: [`lookupflags`](#lookupflags)
  Flags determining the method of how the path is resolved.

- <a href="#path_open.path" name="path_open.path"></a> `path`: `string`
  The relative path of the file or directory to open, relative to the
  [`path_open::fd`](#path_open.fd) directory.

- <a href="#path_open.oflags" name="path_open.oflags"></a> `oflags`: [`oflags`](#oflags)
  The method by which to open the file.

- <a href="#path_open.fs_rights_base" name="path_open.fs_rights_base"></a> `fs_rights_base`: [`rights`](#rights)
  The initial rights of the newly created file descriptor. The
  implementation is allowed to return a file descriptor with fewer rights
  than specified, if and only if those rights do not apply to the type of
  file being opened.
  The *base* rights are rights that will apply to operations using the file
  descriptor itself, while the *inheriting* rights are rights that apply to
  file descriptors derived from it.

- <a href="#path_open.fs_rights_inheriting" name="path_open.fs_rights_inheriting"></a> `fs_rights_inheriting`: [`rights`](#rights)

- <a href="#path_open.fdflags" name="path_open.fdflags"></a> `fdflags`: [`fdflags`](#fdflags)

##### Results
- <a href="#path_open.error" name="path_open.error"></a> `error`: `Result<fd, errno>`
  The file descriptor of the file that has been opened.

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#path_open.error.ok" name="path_open.error.ok"></a> `ok`: [`fd`](#fd)

- <a href="#path_open.error.err" name="path_open.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#path_readlink" name="path_readlink"></a> `path_readlink(fd: fd, path: string, buf: Pointer<u8>, buf_len: size) -> Result<size, errno>`
Read the contents of a symbolic link.
Note: This is similar to `readlinkat` in POSIX.

##### Params
- <a href="#path_readlink.fd" name="path_readlink.fd"></a> `fd`: [`fd`](#fd)

- <a href="#path_readlink.path" name="path_readlink.path"></a> `path`: `string`
  The path of the symbolic link from which to read.

- <a href="#path_readlink.buf" name="path_readlink.buf"></a> `buf`: `Pointer<u8>`
  The buffer to which to write the contents of the symbolic link.

- <a href="#path_readlink.buf_len" name="path_readlink.buf_len"></a> `buf_len`: [`size`](#size)

##### Results
- <a href="#path_readlink.error" name="path_readlink.error"></a> `error`: `Result<size, errno>`
  The number of bytes placed in the buffer.

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#path_readlink.error.ok" name="path_readlink.error.ok"></a> `ok`: [`size`](#size)

- <a href="#path_readlink.error.err" name="path_readlink.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#path_remove_directory" name="path_remove_directory"></a> `path_remove_directory(fd: fd, path: string) -> Result<(), errno>`
Remove a directory.
Return [`errno::notempty`](#errno.notempty) if the directory is not empty.
Note: This is similar to `unlinkat(fd, path, AT_REMOVEDIR)` in POSIX.

##### Params
- <a href="#path_remove_directory.fd" name="path_remove_directory.fd"></a> `fd`: [`fd`](#fd)

- <a href="#path_remove_directory.path" name="path_remove_directory.path"></a> `path`: `string`
  The path to a directory to remove.

##### Results
- <a href="#path_remove_directory.error" name="path_remove_directory.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#path_remove_directory.error.ok" name="path_remove_directory.error.ok"></a> `ok`

- <a href="#path_remove_directory.error.err" name="path_remove_directory.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#path_rename" name="path_rename"></a> `path_rename(fd: fd, old_path: string, new_fd: fd, new_path: string) -> Result<(), errno>`
Rename a file or directory.
Note: This is similar to `renameat` in POSIX.

##### Params
- <a href="#path_rename.fd" name="path_rename.fd"></a> `fd`: [`fd`](#fd)

- <a href="#path_rename.old_path" name="path_rename.old_path"></a> `old_path`: `string`
  The source path of the file or directory to rename.

- <a href="#path_rename.new_fd" name="path_rename.new_fd"></a> `new_fd`: [`fd`](#fd)
  The working directory at which the resolution of the new path starts.

- <a href="#path_rename.new_path" name="path_rename.new_path"></a> `new_path`: `string`
  The destination path to which to rename the file or directory.

##### Results
- <a href="#path_rename.error" name="path_rename.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#path_rename.error.ok" name="path_rename.error.ok"></a> `ok`

- <a href="#path_rename.error.err" name="path_rename.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#path_symlink" name="path_symlink"></a> `path_symlink(old_path: string, fd: fd, new_path: string) -> Result<(), errno>`
Create a symbolic link.
Note: This is similar to `symlinkat` in POSIX.

##### Params
- <a href="#path_symlink.old_path" name="path_symlink.old_path"></a> `old_path`: `string`
  The contents of the symbolic link.

- <a href="#path_symlink.fd" name="path_symlink.fd"></a> `fd`: [`fd`](#fd)

- <a href="#path_symlink.new_path" name="path_symlink.new_path"></a> `new_path`: `string`
  The destination path at which to create the symbolic link.

##### Results
- <a href="#path_symlink.error" name="path_symlink.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#path_symlink.error.ok" name="path_symlink.error.ok"></a> `ok`

- <a href="#path_symlink.error.err" name="path_symlink.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#path_unlink_file" name="path_unlink_file"></a> `path_unlink_file(fd: fd, path: string) -> Result<(), errno>`
Unlink a file.
Return [`errno::isdir`](#errno.isdir) if the path refers to a directory.
Note: This is similar to `unlinkat(fd, path, 0)` in POSIX.

##### Params
- <a href="#path_unlink_file.fd" name="path_unlink_file.fd"></a> `fd`: [`fd`](#fd)

- <a href="#path_unlink_file.path" name="path_unlink_file.path"></a> `path`: `string`
  The path to a file to unlink.

##### Results
- <a href="#path_unlink_file.error" name="path_unlink_file.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#path_unlink_file.error.ok" name="path_unlink_file.error.ok"></a> `ok`

- <a href="#path_unlink_file.error.err" name="path_unlink_file.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#poll_oneoff" name="poll_oneoff"></a> `poll_oneoff(in: ConstPointer<subscription>, out: Pointer<event>, nsubscriptions: size) -> Result<size, errno>`
Concurrently poll for the occurrence of a set of events.

If `nsubscriptions` is 0, returns [`errno::inval`](#errno.inval).

##### Params
- <a href="#poll_oneoff.in" name="poll_oneoff.in"></a> `in`: `ConstPointer<subscription>`
  The events to which to subscribe.

- <a href="#poll_oneoff.out" name="poll_oneoff.out"></a> `out`: `Pointer<event>`
  The events that have occurred.

- <a href="#poll_oneoff.nsubscriptions" name="poll_oneoff.nsubscriptions"></a> `nsubscriptions`: [`size`](#size)
  Both the number of subscriptions and events.

##### Results
- <a href="#poll_oneoff.error" name="poll_oneoff.error"></a> `error`: `Result<size, errno>`
  The number of events stored.

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#poll_oneoff.error.ok" name="poll_oneoff.error.ok"></a> `ok`: [`size`](#size)

- <a href="#poll_oneoff.error.err" name="poll_oneoff.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#proc_exit" name="proc_exit"></a> `proc_exit(rval: exitcode)`
Terminate the process normally. An exit code of 0 indicates successful
termination of the program. The meanings of other values is dependent on
the environment.

##### Params
- <a href="#proc_exit.rval" name="proc_exit.rval"></a> `rval`: [`exitcode`](#exitcode)
  The exit code returned by the process.

##### Results

---

#### <a href="#proc_raise" name="proc_raise"></a> `proc_raise(sig: signal) -> Result<(), errno>`
Send a signal to the process of the calling thread.
Note: This is similar to `raise` in POSIX.

##### Params
- <a href="#proc_raise.sig" name="proc_raise.sig"></a> `sig`: [`signal`](#signal)
  The signal condition to trigger.

##### Results
- <a href="#proc_raise.error" name="proc_raise.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#proc_raise.error.ok" name="proc_raise.error.ok"></a> `ok`

- <a href="#proc_raise.error.err" name="proc_raise.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#sched_yield" name="sched_yield"></a> `sched_yield() -> Result<(), errno>`
Temporarily yield execution of the calling thread.
Note: This is similar to [`sched_yield`](#sched_yield) in POSIX.

##### Params
##### Results
- <a href="#sched_yield.error" name="sched_yield.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#sched_yield.error.ok" name="sched_yield.error.ok"></a> `ok`

- <a href="#sched_yield.error.err" name="sched_yield.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#random_get" name="random_get"></a> `random_get(buf: Pointer<u8>, buf_len: size) -> Result<(), errno>`
Write high-quality random data into a buffer.
This function blocks when the implementation is unable to immediately
provide sufficient high-quality random data.
This function may execute slowly, so when large mounts of random data are
required, it's advisable to use this function to seed a pseudo-random
number generator, rather than to provide the random data directly.

##### Params
- <a href="#random_get.buf" name="random_get.buf"></a> `buf`: `Pointer<u8>`
  The buffer to fill with random data.

- <a href="#random_get.buf_len" name="random_get.buf_len"></a> `buf_len`: [`size`](#size)

##### Results
- <a href="#random_get.error" name="random_get.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#random_get.error.ok" name="random_get.error.ok"></a> `ok`

- <a href="#random_get.error.err" name="random_get.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#sock_accept" name="sock_accept"></a> `sock_accept(fd: fd, flags: fdflags) -> Result<fd, errno>`
Accept a new incoming connection.
Note: This is similar to `accept` in POSIX.

##### Params
- <a href="#sock_accept.fd" name="sock_accept.fd"></a> `fd`: [`fd`](#fd)
  The listening socket.

- <a href="#sock_accept.flags" name="sock_accept.flags"></a> `flags`: [`fdflags`](#fdflags)
  The desired values of the file descriptor flags.

##### Results
- <a href="#sock_accept.error" name="sock_accept.error"></a> `error`: `Result<fd, errno>`
  New socket connection

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#sock_accept.error.ok" name="sock_accept.error.ok"></a> `ok`: [`fd`](#fd)

- <a href="#sock_accept.error.err" name="sock_accept.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#sock_recv" name="sock_recv"></a> `sock_recv(fd: fd, ri_data: iovec_array, ri_flags: riflags) -> Result<(size, roflags), errno>`
Receive a message from a socket.
Note: This is similar to `recv` in POSIX, though it also supports reading
the data into multiple buffers in the manner of `readv`.

##### Params
- <a href="#sock_recv.fd" name="sock_recv.fd"></a> `fd`: [`fd`](#fd)

- <a href="#sock_recv.ri_data" name="sock_recv.ri_data"></a> `ri_data`: [`iovec_array`](#iovec_array)
  List of scatter/gather vectors to which to store data.

- <a href="#sock_recv.ri_flags" name="sock_recv.ri_flags"></a> `ri_flags`: [`riflags`](#riflags)
  Message flags.

##### Results
- <a href="#sock_recv.error" name="sock_recv.error"></a> `error`: `Result<(size, roflags), errno>`
  Number of bytes stored in ri_data and message flags.

###### Variant Layout
- size: 12
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#sock_recv.error.ok" name="sock_recv.error.ok"></a> `ok`: `(size, roflags)`

####### Record members
- <a href="#sock_recv.error.ok.0" name="sock_recv.error.ok.0"></a> `0`: [`size`](#size)

Offset: 0

- <a href="#sock_recv.error.ok.1" name="sock_recv.error.ok.1"></a> `1`: [`roflags`](#roflags)

Offset: 4

- <a href="#sock_recv.error.err" name="sock_recv.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#sock_send" name="sock_send"></a> `sock_send(fd: fd, si_data: ciovec_array, si_flags: siflags) -> Result<size, errno>`
Send a message on a socket.
Note: This is similar to `send` in POSIX, though it also supports writing
the data from multiple buffers in the manner of `writev`.

##### Params
- <a href="#sock_send.fd" name="sock_send.fd"></a> `fd`: [`fd`](#fd)

- <a href="#sock_send.si_data" name="sock_send.si_data"></a> `si_data`: [`ciovec_array`](#ciovec_array)
  List of scatter/gather vectors to which to retrieve data

- <a href="#sock_send.si_flags" name="sock_send.si_flags"></a> `si_flags`: [`siflags`](#siflags)
  Message flags.

##### Results
- <a href="#sock_send.error" name="sock_send.error"></a> `error`: `Result<size, errno>`
  Number of bytes transmitted.

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#sock_send.error.ok" name="sock_send.error.ok"></a> `ok`: [`size`](#size)

- <a href="#sock_send.error.err" name="sock_send.error.err"></a> `err`: [`errno`](#errno)


---

#### <a href="#sock_shutdown" name="sock_shutdown"></a> `sock_shutdown(fd: fd, how: sdflags) -> Result<(), errno>`
Shut down socket send and receive channels.
Note: This is similar to `shutdown` in POSIX.

##### Params
- <a href="#sock_shutdown.fd" name="sock_shutdown.fd"></a> `fd`: [`fd`](#fd)

- <a href="#sock_shutdown.how" name="sock_shutdown.how"></a> `how`: [`sdflags`](#sdflags)
  Which channels on the socket to shut down.

##### Results
- <a href="#sock_shutdown.error" name="sock_shutdown.error"></a> `error`: `Result<(), errno>`

###### Variant Layout
- size: 8
- align: 4
- tag_size: 4
###### Variant cases
- <a href="#sock_shutdown.error.ok" name="sock_shutdown.error.ok"></a> `ok`

- <a href="#sock_shutdown.error.err" name="sock_shutdown.error.err"></a> `err`: [`errno`](#errno)

