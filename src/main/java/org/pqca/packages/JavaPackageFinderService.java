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
import java.io.File;
import java.nio.file.Path;

public class JavaPackageFinderService extends PackageFinderService {

    public JavaPackageFinderService(@Nonnull File rootFile) throws IllegalArgumentException {
        super(rootFile);
    }

    @Override
    public boolean isBuildFile(@Nonnull Path file) {
        return file.endsWith("pom.xml")
                || file.endsWith("build.gradle")
                || file.endsWith("build.gradle.kts");
    }
}
