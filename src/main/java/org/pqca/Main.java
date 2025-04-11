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
package org.pqca;

import jakarta.annotation.Nonnull;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.pqca.errors.CouldNotLoadJavaJars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("java:S106")
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(@Nonnull String[] args) {
        final String workspace = System.getenv("GITHUB_WORKSPACE");
        final File projectDirectory = new File(workspace);

        // Create output dir
        final File outputDir =
                Optional.ofNullable(System.getenv("CBOMKIT_OUT_DIR"))
                        .map(File::new)
                        .orElse(new File("cbom"));
        boolean mkdirs_ok = outputDir.mkdirs();
        if (mkdirs_ok) {
            LOG.info("Created cbom output dir '{}'", outputDir);
        } else {
            LOG.error("Failed to created cbom output dir '{}'", outputDir);
        }

        final BomGenerator bomGenerator = new BomGenerator(projectDirectory, outputDir);

        try {
            List<Bom> boms = new ArrayList<>();
            boms.addAll(bomGenerator.generateJavaBoms());
            boms.addAll(bomGenerator.generatePythonBoms());

            Bom consolidatedBom = createCombinedBom(boms);
            bomGenerator.writeBom(consolidatedBom);
        } catch (CouldNotLoadJavaJars e) {
            LOG.error(e.getMessage(), e);
        }

        // set output var
        final String githubOutput = System.getenv("GITHUB_OUTPUT");
        try (final FileWriter outPutVarFileWriter = new FileWriter(githubOutput, true)) {
            outPutVarFileWriter.write("pattern=" + outputDir + "/cbom*.json\n");
            LOG.info("pattern=" + outputDir + "/cbom*.json > ' + githubOutput);
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    @Nonnull
    private static Bom createCombinedBom(@Nonnull List<Bom> sourceBoms) {
        final Bom bom = new Bom();
        bom.setSerialNumber("urn:uuid:" + UUID.randomUUID());

        final List<Component> components = new ArrayList<>();
        final List<Dependency> dependencies = new ArrayList<>();
        for (final Bom sourceBom : sourceBoms) {
            components.addAll(sourceBom.getComponents());
            dependencies.addAll(sourceBom.getDependencies());
        }
        bom.setComponents(components);
        bom.setDependencies(dependencies);
        return bom;
    }
}
