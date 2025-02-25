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
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.cyclonedx.Version;
import org.cyclonedx.exception.GeneratorException;
import org.cyclonedx.generators.BomGeneratorFactory;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.OrganizationalEntity;
import org.cyclonedx.model.Service;
import org.cyclonedx.model.metadata.ToolInformation;
import org.pqca.errors.CBOMWasEmpty;
import org.pqca.errors.CouldNotLoadJavaJars;
import org.pqca.errors.CouldNotWriteCBOMToOutput;
import org.pqca.indexing.JavaIndexService;
import org.pqca.indexing.ProjectModule;
import org.pqca.packages.MavenPackageFinderService;
import org.pqca.packages.PackageMetadata;
import org.pqca.scanning.java.JavaScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("java:S106")
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final String ACTION_NAME = "CBOMkit-action";
    private static final String ACTION_ORG = "PQCA";

    public static void main(@Nonnull String[] args) {
        final String workspace = System.getenv("GITHUB_WORKSPACE");
        final File projectDirectory = new File(workspace);

        try {
            final List<File> javaJars = getJavaJars();
            final JavaIndexService javaIndexService = new JavaIndexService(projectDirectory);

            // identify packages
            MavenPackageFinderService packageFinder =
                    new MavenPackageFinderService(projectDirectory);
            for (Path p : packageFinder.findPackages()) {
                Optional<PackageMetadata> metadata = packageFinder.getMetadata(p);
                if (!metadata.isPresent()) {
                    continue;
                }

                try {
                    Path packagePath = p.equals(projectDirectory.toPath()) ? p : p.getParent();
                    LOG.info("Package path is: {}", packagePath.toString());

                    // java
                    final List<ProjectModule> javaProjectModules =
                            javaIndexService.index(packagePath);
                    if (javaProjectModules.isEmpty()) {
                        LOG.info("Skipping - no projects found");
                        continue;
                    }
                    final JavaScannerService javaScannerService =
                            new JavaScannerService(javaJars, packagePath.toFile());
                    final Bom javaBom = javaScannerService.scan(null, javaProjectModules);

                    // TODO: python

                    final Bom bom = createCombinedBom(List.of(javaBom));

                    writeBom(metadata, bom);
                } catch (GeneratorException | CBOMWasEmpty | CouldNotWriteCBOMToOutput e) {
                    LOG.error(e.getMessage(), e);
                }
            }
        } catch (CouldNotLoadJavaJars e) {
            LOG.error(e.getMessage(), e);
            return;
        }

        // set output var
        final String githubOutput = System.getenv("GITHUB_OUTPUT");
        try (final FileWriter outPutVarFileWriter = new FileWriter(githubOutput, true)) {
            outPutVarFileWriter.write("pattern=cbom*.json\n");
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private static void writeBom(Optional<PackageMetadata> packageMetadata, Bom bom)
            throws GeneratorException, CouldNotWriteCBOMToOutput, CBOMWasEmpty {
        final String fileName =
                packageMetadata
                        .map(
                                pm ->
                                        String.format(
                                                "cbom_%s_%s_%s.json",
                                                pm.namespace(), pm.name(), pm.version()))
                        .orElse("cbom.json");

        final BomJsonGenerator bomGenerator =
                BomGeneratorFactory.createJson(Version.VERSION_16, bom);
        @Nullable String bomString = bomGenerator.toJsonString();
        // LOG.info(bomString);
        if (bomString == null) {
            throw new CBOMWasEmpty();
        }

        LOG.info(
                "Writing cbom {} with {} components",
                fileName,
                bom.getComponents() == null ? 0 : bom.getComponents().size());
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(bomString);
        } catch (IOException e) {
            throw new CouldNotWriteCBOMToOutput(e);
        }
    }

    @Nonnull
    private static Bom createCombinedBom(@Nonnull List<Bom> sourceBoms) {
        final Bom bom = new Bom();
        bom.setSerialNumber("urn:uuid:" + UUID.randomUUID());

        final Metadata metadata = new Metadata();
        metadata.setTimestamp(new Date());

        final ToolInformation scannerInfo = new ToolInformation();
        final Service scannerService = new Service();
        scannerService.setName(ACTION_NAME);

        final OrganizationalEntity organization = new OrganizationalEntity();
        organization.setName(ACTION_ORG);
        scannerService.setProvider(organization);
        scannerInfo.setServices(List.of(scannerService));
        metadata.setToolChoice(scannerInfo);
        bom.setMetadata(metadata);

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

    @Nonnull
    private static List<File> getJavaJars() throws CouldNotLoadJavaJars {
        final String directoryPath = System.getenv("CBOMKIT_JAVA_JAR_DIR");
        final File directory = new File(directoryPath);
        final FileFilter jarFilter =
                file -> file.isFile() && file.getName().toLowerCase().endsWith(".jar");
        final File[] jars = directory.listFiles(jarFilter);
        if (jars == null || jars.length == 0) {
            throw new CouldNotLoadJavaJars(directory.toPath());
        }
        LOG.info("Loaded {} jars", jars.length);
        return List.of(jars);
    }
}
