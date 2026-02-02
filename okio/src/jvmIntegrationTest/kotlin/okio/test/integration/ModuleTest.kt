package okio.test.integration

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModuleTest {
  @Test
  fun testModule() {
    // assert okio is modular
    val okioModule = ModuleLayer.boot().modules().single { it.name == "okio" }
    assertFalse(okioModule.descriptor.isAutomatic)
    assertTrue(okioModule.isExported("okio"))
    assertFalse(okioModule.isExported("okio.internal"))
  }
}
