/*
 * CBOMkit-action
 * Copyright (C) 2024 PQCA
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
package org.pqca.scanning.python;

import jakarta.annotation.Nonnull;
import java.io.File;
import java.util.List;
import org.cyclonedx.model.Bom;
import org.pqca.indexing.ProjectModule;
import org.pqca.scanning.ScannerService;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.plugins.python.api.PythonCheck;
import org.sonar.plugins.python.api.PythonVisitorContext;
import org.sonar.plugins.python.api.tree.FileInput;

public final class PythonScannerService extends ScannerService {

    public PythonScannerService(@Nonnull File projectDirectory) {
        super(projectDirectory);
    }

    @Override
    public @Nonnull Bom scan(@Nonnull List<ProjectModule> index) {
        final PythonCheck visitor = new PythonDetectionCollectionRule(this);

        LOGGER.info("Start scanning {} python projects", index.size());

        int counter = 1;
        for (ProjectModule project : index) {
            final String projectStr =
                    project.identifier() + " (" + counter + "/" + index.size() + ")";
            LOGGER.info("Scanning project " + projectStr);

            for (InputFile inputFile : project.inputFileList()) {
                final PythonScannableFile pythonScannableFile = new PythonScannableFile(inputFile);
                final FileInput parsedFile = pythonScannableFile.parse();
                final PythonVisitorContext context =
                        new PythonVisitorContext(
                                parsedFile,
                                pythonScannableFile,
                                this.projectDirectory,
                                project.identifier());
                visitor.scanFile(context);
            }
            counter++;
        }

        return this.getBOM();
    }
}
