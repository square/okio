package okio.test.integration

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleTest {
  @Test
  fun testModule() {
    // assert okio is modular
    val okioModule = ModuleLayer.boot().modules().single { it.name == "okio" }
    assertFalse(okioModule.descriptor.isAutomatic)
    assertTrue(okioModule.isExported("okio"))
    assertFalse(okioModule.isExported("okio.internal"))
  }

  @Test
  fun testLoggerClassIsPresent() {
    val logger = Class.forName("okio.internal.Logger")
    assertNotNull(logger.getResource("/META-INF/versions/9/okio/internal/Logger.class"))
  }
}
