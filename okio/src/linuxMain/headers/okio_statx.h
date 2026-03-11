/*
 * Symbols to call statx() via syscall().
 *
 * Using constant values from Chromium.
 * https://chromium.googlesource.com/chromiumos/docs/+/master/constants/syscalls.md
 */
#ifdef __x86_64__
#define __NR_statx 332
#elif defined(__arm__)
#define __NR_statx 397
#elif defined(__aarch64__)
#define __NR_statx 291
#elif defined(__i386__)
#define __NR_statx 383
#else
#error "unexpected arch for __NR_statx"
#endif

#define AT_FDCWD -100
#define AT_SYMLINK_NOFOLLOW	0x100
