/*
 * Copyright (C) 2014 Square, Inc.
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
 */
package okio.osgi;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import javax.inject.Inject;

import static okio.osgi.OSGiHelper.findBundleBySymbolicName;
import static okio.osgi.OSGiHelper.mavenBundleAsInProject;
import static okio.osgi.OSGiHelper.workspaceBundle;
import static org.junit.Assert.*;
import static org.ops4j.pax.exam.CoreOptions.*;

/**
 * @author lburgazzoli
 */
@RunWith(PaxExam.class)
public class OSGiBundleTest {
    @Inject
    BundleContext context;

    @Configuration
    public Option[] config() {
        return options(
            systemProperty("org.osgi.framework.storage.clean").value("true"),
            systemProperty("org.ops4j.pax.logging.DefaultServiceLog.level").value("WARN"),
            mavenBundleAsInProject("org.slf4j","slf4j-api"),
            mavenBundleAsInProject("org.slf4j","slf4j-simple").noStart(),
            workspaceBundle(),
            junitBundles(),
            cleanCaches()
        );
    }

    @Test
    public void checkInject() {
        assertNotNull(context);
    }

    @Test
    public void checkBundleState() {
        Bundle bundle = findBundleBySymbolicName(context, "com.squareup.okio.okio");

        assertNotNull(bundle);
        assertEquals(bundle.getState(), Bundle.ACTIVE);
    }

    @Test
    public void checkBundleExports() {
        Bundle bundle = findBundleBySymbolicName(context, "com.squareup.okio.okio");
        assertNotNull(bundle);

        final String exports = bundle.getHeaders().get("Export-Package");
        final String[] packages = exports.split(",");

        assertTrue(packages.length == 1);
        assertTrue(packages[0].startsWith("okio;"));
    }
}
