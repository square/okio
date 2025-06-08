package okio

/*
 * The current process's host file system. Use this instance directly, or dependency inject a
 * [FileSystem] to make code testable.
 */
expect val FileSystem.Companion.SYSTEM: FileSystem
