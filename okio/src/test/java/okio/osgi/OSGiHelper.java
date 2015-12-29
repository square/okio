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
 * limitations under the License.
 */
package okio.osgi;

import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.options.MavenArtifactProvisionOption;
import org.ops4j.pax.exam.util.PathUtils;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * @author lburgazzoli
 */
public class OSGiHelper {

    public static Option workspaceBundle() {
        return CoreOptions.bundle("reference:file:" + PathUtils.getBaseDir() + "/target/classes");
    }

    public static MavenArtifactProvisionOption mavenBundleAsInProject(final String groupId,final String artifactId) {
        return CoreOptions.mavenBundle().groupId(groupId).artifactId(artifactId).versionAsInProject();
    }

    public static Bundle findBundleBySymbolicName(BundleContext context, String symbolicName) {
        for (Bundle bundle : context.getBundles()) {
            if (bundle != null) {
                if (bundle.getSymbolicName().equalsIgnoreCase(symbolicName)) {
                    return bundle;
                }
            }
        }

        return null;
    }
}
