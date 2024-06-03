package okio.test.integration;

import okio.FileSystem;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleTest {
  @Test
  public void test() {
    // test okio.test.integration is modular
    assertTrue(ModuleTest.class.getModule().isNamed());
    assertEquals(ModuleTest.class.getModule().getName(), "okio.test.integration");
    assertFalse(ModuleTest.class.getModule().getDescriptor().isAutomatic());
    // test okio is modular
    assertTrue(FileSystem.class.getModule().isNamed());
    assertEquals(FileSystem.class.getModule().getName(), "okio");
    assertFalse(FileSystem.class.getModule().getDescriptor().isAutomatic());
  }
}
