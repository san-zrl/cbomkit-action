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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

public class PythonPackageFinderService extends PackageFinderService {

    private static final Pattern NAME_PATTERN = Pattern.compile("name\\s*=\\s*['\"]([^'\"]*)['\"]");
    private static final Pattern VERSION_PATTERN =
            Pattern.compile("version\\s*=\\s*['\"]([^'\"]*)['\"]");

    public PythonPackageFinderService(@Nonnull File rootFile) throws IllegalArgumentException {
        super(rootFile);
    }

    @Override
    public boolean isBuildFile(@Nonnull Path file) {
        return file.endsWith("pyproject.toml")
                || file.endsWith("setup.cfg")
                || file.endsWith("setup.py");
    }

    @Override
    @Nullable public PackageMetadata getMetadata(@Nonnull Path buildFile) {
        try {
            if (buildFile.endsWith("pyproject.toml")) {
                TomlParseResult result = Toml.parse(buildFile);
                final String name = result.getString("project.name");
                if (name != null) {
                    return new PackageMetadata(
                            buildFile.getParent().toFile(),
                            null,
                            name,
                            result.getString("project.version"));
                }
            } else if (buildFile.endsWith("setup.cfg") || buildFile.endsWith("setup.py")) {
                final String name = findAttribute(NAME_PATTERN, buildFile);
                if (name != null) {
                    return new PackageMetadata(
                            buildFile.getParent().toFile(),
                            null,
                            name,
                            findAttribute(VERSION_PATTERN, buildFile));
                }
            }
        } catch (Exception e) {
            // nothing
        }
        return null;
    }

    @Nullable private String findAttribute(@Nonnull Pattern pattern, @Nonnull Path buildFile)
            throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(buildFile.toFile()))) {
            String line;

            while ((line = reader.readLine()) != null) {
                final Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }
}
