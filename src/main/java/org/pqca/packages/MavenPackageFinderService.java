/*
 * CBOMkit-action
 * Copyright (C) 2025 PQCA
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pqca.packages;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;

public class MavenPackageFinderService extends PackageFinderService {
    @Nonnull private final MavenXpp3Reader reader;

    public MavenPackageFinderService(@Nonnull File rootFile) throws IllegalArgumentException {
        super(rootFile);
        this.reader = new MavenXpp3Reader();
    }

    @Override
    public boolean isBuildFile(@Nonnull Path file) {
        return file.endsWith("pom.xml")
                || file.endsWith("build.gradle")
                || file.endsWith("build.gradle.kts");
    }

    @Override
    @Nullable public PackageMetadata getMetadata(@Nonnull Path buildFile) {
        if (buildFile.endsWith("pom.xml")) {
            try {
                final Model model = reader.read(new FileReader(buildFile.toFile()));
                final String artifactId = model.getArtifactId();
                if (!artifactId.endsWith("-parent")) {
                    String groupId = null;
                    String version = null;
                    Parent parent = model.getParent();
                    if (parent != null) {
                        groupId = parent.getGroupId();
                        version = parent.getVersion();
                    }
                    if (model.getGroupId() != null) {
                        groupId = model.getGroupId();
                    }
                    if (model.getVersion() != null) {
                        version = model.getVersion();
                    }
                    return new PackageMetadata(
                            buildFile.getParent().toFile(), groupId, artifactId, version);
                }
            } catch (Exception e) {
                // nothing
            }
        } else {
            String name =
                    buildFile
                            .getParent()
                            .toString()
                            .replaceFirst("^" + root.toString() + "/", "")
                            .replaceFirst("/src$", "")
                            .replaceAll("/", ".");
            return new PackageMetadata(buildFile.getParent().toFile(), null, name, null);
        }
        return null;
    }
}
