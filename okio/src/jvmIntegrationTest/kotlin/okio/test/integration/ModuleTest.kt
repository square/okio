package okio.test.integration

import okio.FileSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleTest {
  @Test
  fun testModule() {
    // test okio.test.integration is modular
    assertTrue(ModuleTest::class.java.module.isNamed)
    assertEquals(ModuleTest::class.java.module.name, "okio.test.integration")
    assertFalse(ModuleTest::class.java.module.descriptor.isAutomatic)
    // test okio is modular
    assertTrue(FileSystem::class.java.module.isNamed)
    assertEquals(FileSystem::class.java.module.name, "okio")
    assertFalse(FileSystem::class.java.module.descriptor.isAutomatic)
  }
}
