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

import com.ibm.plugin.ScannerManager;
import jakarta.annotation.Nonnull;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import org.cyclonedx.Version;
import org.cyclonedx.exception.GeneratorException;
import org.cyclonedx.generators.BomGeneratorFactory;
import org.cyclonedx.generators.json.BomJsonGenerator;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Metadata;
import org.cyclonedx.model.OrganizationalEntity;
import org.cyclonedx.model.Property;
import org.cyclonedx.model.Service;
import org.cyclonedx.model.metadata.ToolInformation;
import org.pqca.errors.CouldNotLoadJavaJars;
import org.pqca.indexing.JavaIndexService;
import org.pqca.indexing.ProjectModule;
import org.pqca.indexing.PythonIndexService;
import org.pqca.packages.MavenPackageFinderService;
import org.pqca.packages.PackageMetadata;
import org.pqca.packages.PythonPackageFinderService;
import org.pqca.scanning.java.JavaScannerService;
import org.pqca.scanning.python.PythonScannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BomGenerator {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final String ACTION_NAME = "CBOMkit-action";
    private static final String ACTION_ORG = "PQCA";

    private static final ScannerManager scannerMgr = new ScannerManager(null);

    @Nonnull private final File projectDirectory;

    public BomGenerator(@Nonnull File projectDirectory) {
        this.projectDirectory = projectDirectory;
    }

    @Nonnull
    public Bom generateJavaBoms() throws CouldNotLoadJavaJars {
        final JavaIndexService javaIndexService = new JavaIndexService(projectDirectory);
        final List<ProjectModule> javaProjectModules = javaIndexService.index();

        final List<File> javaJars = getJavaJars();
        final MavenPackageFinderService packageFinder =
                new MavenPackageFinderService(projectDirectory);
        for (PackageMetadata pm : packageFinder.findPackages()) {
            LOG.info("Scanning maven package {}", pm.packageDir());
            final List<ProjectModule> packageModules =
                    getPackageModules(javaProjectModules, pm.packageDir());
            if (!packageModules.isEmpty()) {
                final JavaScannerService javaScannerService =
                        new JavaScannerService(javaJars, pm.packageDir());
                final Bom javaBom = javaScannerService.scan(packageModules);
                writeBom(pm, javaBom);
            } else {
                LOG.info("No java source code to scan.");
            }
        }

        final JavaScannerService javaScannerService =
                new JavaScannerService(javaJars, projectDirectory);
        return javaScannerService.scan(javaProjectModules);
    }

    @Nonnull
    public Bom generatePythonBoms() {
        final PythonIndexService pythonIndexService = new PythonIndexService(projectDirectory);
        final List<ProjectModule> pythonProjectModules = pythonIndexService.index();

        final PythonPackageFinderService packageFinder =
                new PythonPackageFinderService(projectDirectory);
        for (PackageMetadata pm : packageFinder.findPackages()) {
            LOG.info("Scanning python package {}", pm.packageDir());
            final List<ProjectModule> packageModules =
                    getPackageModules(pythonProjectModules, pm.packageDir());
            if (!packageModules.isEmpty()) {
                final PythonScannerService pythonScannerService =
                        new PythonScannerService(pm.packageDir());
                final Bom pythonBom = pythonScannerService.scan(packageModules);
                writeBom(pm, pythonBom);
            } else {
                LOG.info("No python source code to scan.");
            }
        }

        final PythonScannerService pythonScannerService =
                new PythonScannerService(projectDirectory);
        return pythonScannerService.scan(pythonProjectModules);
    }

    private List<ProjectModule> getPackageModules(List<ProjectModule> allModules, File packageDir) {
        return allModules.stream()
                .filter(
                        pm ->
                                projectDirectory
                                        .toPath()
                                        .resolve(pm.identifier())
                                        .startsWith(packageDir.toPath()))
                .toList();
    }

    public void writeBom(Bom bom) {
        writeBom(new PackageMetadata(projectDirectory, null, null, null), bom);
    }

    private void writeBom(PackageMetadata packageMetadata, Bom bom) {
        bom.setMetadata(generateMetadata(packageMetadata));

        final BomJsonGenerator bomGenerator =
                BomGeneratorFactory.createJson(Version.VERSION_16, bom);

        try {
            String bomString = bomGenerator.toJsonString();
            if (bomString == null) {
                LOG.error("Empty CBOM");
            } else {
                final String fileName = packageMetadata.getCbomFileName();
                LOG.info(
                        "Writing cbom {} with {} components",
                        fileName,
                        bom.getComponents() == null ? 0 : bom.getComponents().size());
                try (FileWriter writer = new FileWriter(fileName)) {
                    writer.write(bomString);
                }
            }
        } catch (IOException | GeneratorException e) {
            LOG.error(e.getMessage(), e);
        }
        scannerMgr.reset();
    }

    private Metadata generateMetadata(PackageMetadata packageMetadata) {
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

        final String gitUrl = System.getenv("GITHUB_REPOSITORY");
        if (gitUrl != null) {
            final Property gitUrlProperty = new Property();
            gitUrlProperty.setName("gitUrl");
            gitUrlProperty.setValue(gitUrl);
            metadata.addProperty(gitUrlProperty);
        }

        final String revision = System.getenv("GITHUB_REF_NAME");
        if (revision != null) {
            final Property revisionProperty = new Property();
            revisionProperty.setName("revision");
            revisionProperty.setValue(revision);
            metadata.addProperty(revisionProperty);
        }

        final String commit = System.getenv("GITHUB_SHA");
        if (commit != null) {
            final Property commitProperty = new Property();
            commitProperty.setName("commit");
            commitProperty.setValue(commit.substring(0, 7));
            metadata.addProperty(commitProperty);
        }

        if (!packageMetadata.packageDir().equals(projectDirectory)) {
            final Path relPackageDir =
                    packageMetadata.packageDir().toPath().relativize(projectDirectory.toPath());
            final Property packageFolderProperty = new Property();
            packageFolderProperty.setName("packageFolder");
            packageFolderProperty.setValue(relPackageDir.toString());
            metadata.addProperty(packageFolderProperty);
        }

        return metadata;
    }

    @Nonnull
    private static List<File> getJavaJars() throws CouldNotLoadJavaJars {
        final String directoryPath = System.getenv("CBOMKIT_JAVA_JAR_DIR");
        final File directory = new File(directoryPath);
        final FileFilter jarFilter =
                file -> file.isFile() && file.getName().toLowerCase().endsWith(".jar");
        final File[] jars = directory.listFiles(jarFilter);
        if (jars == null || jars.length == 0) {
            throw new CouldNotLoadJavaJars(directory);
        }
        LOG.info("Loaded {} jars", jars.length);
        return List.of(jars);
    }
}
