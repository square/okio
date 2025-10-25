package okio

import kotlin.annotation.AnnotationTarget.FUNCTION

@RequiresOptIn
@Target(FUNCTION)
annotation class UnsafePathApi
