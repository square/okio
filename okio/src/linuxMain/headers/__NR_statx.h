/*
 * Declare a __NR_statx symbol to build against, assuming something will somehow provide an actual
 * symbol.
 */
#include <sys/syscall.h>

#ifndef __NR_statx
#define __NR_statx -1
#endif
