/*
 * Copyright (C) 2017-2018 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.dac.resource;

import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.Objects;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;

import com.dremio.dac.annotations.RestResource;
import com.dremio.dac.annotations.Secured;
import com.dremio.dac.explore.DatasetsResource;
import com.dremio.dac.explore.QueryExecutor;
import com.dremio.dac.explore.model.FileFormatUI;
import com.dremio.dac.explore.model.InitialPreviewResponse;
import com.dremio.dac.model.folder.Folder;
import com.dremio.dac.model.folder.SourceFolderPath;
import com.dremio.dac.model.job.JobDataFragment;
import com.dremio.dac.model.sources.PhysicalDataset;
import com.dremio.dac.model.sources.PhysicalDatasetPath;
import com.dremio.dac.model.sources.SourceName;
import com.dremio.dac.model.sources.SourcePath;
import com.dremio.dac.model.sources.SourceUI;
import com.dremio.dac.service.errors.ClientErrorException;
import com.dremio.dac.service.errors.DatasetNotFoundException;
import com.dremio.dac.service.errors.DatasetVersionNotFoundException;
import com.dremio.dac.service.errors.NewDatasetQueryException;
import com.dremio.dac.service.errors.PhysicalDatasetNotFoundException;
import com.dremio.dac.service.errors.SourceFileNotFoundException;
import com.dremio.dac.service.errors.SourceFolderNotFoundException;
import com.dremio.dac.service.errors.SourceNotFoundException;
import com.dremio.dac.service.source.SourceService;
import com.dremio.exec.catalog.Catalog;
import com.dremio.exec.catalog.ConnectionReader;
import com.dremio.file.File;
import com.dremio.file.SourceFilePath;
import com.dremio.service.namespace.BoundedDatasetCount;
import com.dremio.service.namespace.NamespaceException;
import com.dremio.service.namespace.NamespaceKey;
import com.dremio.service.namespace.NamespaceNotFoundException;
import com.dremio.service.namespace.NamespaceService;
import com.dremio.service.namespace.SourceState;
import com.dremio.service.namespace.dataset.proto.AccelerationSettings;
import com.dremio.service.namespace.dataset.proto.DatasetType;
import com.dremio.service.namespace.file.FileFormat;
import com.dremio.service.namespace.physicaldataset.proto.PhysicalDatasetConfig;
import com.dremio.service.namespace.source.proto.SourceConfig;
import com.dremio.service.reflection.ReflectionService;
import com.dremio.service.reflection.proto.ReflectionGoal;

/**
 * Rest resource for sources.
 */
@RestResource
@Secured
@RolesAllowed({"admin", "user"})
@Path("/source/{sourceName}")
public class SourceResource {
//  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SourceResource.class);

  private final QueryExecutor executor;
  private final Provider<NamespaceService> namespaceService;
  private final Provider<ReflectionService> reflectionService;
  private final SourceService sourceService;
  private final SourceName sourceName;
  private final SecurityContext securityContext;
  private final SourcePath sourcePath;
  private final DatasetsResource datasetsResource;
  private final ConnectionReader cReader;
  private final Catalog catalog;

  @Inject
  public SourceResource(
      Provider<NamespaceService> namespaceService,
      Provider<ReflectionService> reflectionService,
      SourceService sourceService,
      @PathParam("sourceName") SourceName sourceName,
      QueryExecutor executor,
      SecurityContext securityContext,
      DatasetsResource datasetsResource,
      ConnectionReader cReader,
      Catalog catalog
      ) throws SourceNotFoundException {
    this.namespaceService = namespaceService;
    this.reflectionService = reflectionService;
    this.sourceService = sourceService;
    this.sourceName = sourceName;
    this.securityContext = securityContext;
    this.datasetsResource = datasetsResource;
    this.sourcePath = new SourcePath(sourceName);
    this.executor = executor;
    this.cReader = cReader;
    this.catalog = catalog;
  }

  protected SourceUI newSource(SourceConfig config) throws Exception {
    return SourceUI.get(config, cReader);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public SourceUI getSource(@QueryParam("includeContents") @DefaultValue("true") boolean includeContents)
      throws Exception {
    try {
      final SourceConfig config = namespaceService.get().getSource(sourcePath.toNamespaceKey());
      final SourceState sourceState = sourceService.getSourceState(sourcePath.getSourceName().getName());
      if (sourceState == null) {
        throw new SourceNotFoundException(sourcePath.getSourceName().getName());
      }

      final BoundedDatasetCount datasetCount = namespaceService.get().getDatasetCount(new NamespaceKey(config.getName()), BoundedDatasetCount.SEARCH_TIME_LIMIT_MS, BoundedDatasetCount.COUNT_LIMIT_TO_STOP_SEARCH);
      final SourceUI source = newSource(config)
          .setNumberOfDatasets(datasetCount.getCount());
      source.setDatasetCountBounded(datasetCount.isCountBound() || datasetCount.isTimeBound());

      source.setState(sourceState);

      final AccelerationSettings settings = reflectionService.get().getReflectionSettings().getReflectionSettings(sourcePath.toNamespaceKey());
      if (settings != null) {
        source.setAccelerationRefreshPeriod(settings.getRefreshPeriod());
        source.setAccelerationGracePeriod(settings.getGracePeriod());
      }
      if (includeContents) {
        source.setContents(sourceService.listSource(sourcePath.getSourceName(),
          namespaceService.get().getSource(sourcePath.toNamespaceKey()), securityContext.getUserPrincipal().getName()));
      }
      return source;
    } catch (NamespaceNotFoundException nfe) {
      throw new SourceNotFoundException(sourcePath.getSourceName().getName(), nfe);
    }
  }

  @RolesAllowed("admin")
  @DELETE
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteSource(@QueryParam("version") Long version) throws NamespaceException, SourceNotFoundException {
    if (version == null) {
      throw new ClientErrorException("missing version parameter");
    }
    try {
      SourceConfig config = namespaceService.get().getSource(new SourcePath(sourceName).toNamespaceKey());
      if(!Objects.equals(config.getVersion(), version)) {
        throw new ConcurrentModificationException(String.format("Unable to delete source, expected version %s, received version %s.", config.getVersion(), version));
      }
      catalog.deleteSource(config);
    } catch (NamespaceNotFoundException nfe) {
      throw new SourceNotFoundException(sourcePath.getSourceName().getName(), nfe);
    }
  }

  @GET
  @Path("/folder/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  public Folder getFolder(@PathParam("path") String path, @QueryParam("includeContents") @DefaultValue("true") boolean includeContents)
      throws NamespaceException, IOException, SourceFolderNotFoundException, PhysicalDatasetNotFoundException, SourceNotFoundException {
    sourceService.checkSourceExists(sourceName);
    SourceFolderPath folderPath = SourceFolderPath.fromURLPath(sourceName, path);
    return sourceService.getFolder(sourceName, folderPath, includeContents, securityContext.getUserPrincipal().getName());
  }

  @GET
  @Path("/dataset/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  public PhysicalDataset getPhysicalDataset(@PathParam("path") String path)
      throws SourceNotFoundException, NamespaceException {
    sourceService.checkSourceExists(sourceName);
    PhysicalDatasetPath datasetPath = PhysicalDatasetPath.fromURLPath(sourceName, path);
    return sourceService.getPhysicalDataset(sourceName, datasetPath);
  }

  @GET
  @Path("/file/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  public File getFile(@PathParam("path") String path)
      throws SourceNotFoundException, NamespaceException, PhysicalDatasetNotFoundException {
    sourceService.checkSourceExists(sourceName);

    final SourceFilePath filePath = SourceFilePath.fromURLPath(sourceName, path);
    return sourceService.getFileDataset(sourceName, filePath, null);
  }

  // format settings on a file.
  @GET
  @Path("/file_format/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  public FileFormatUI getFormatSettings(@PathParam("path") String path)
      throws SourceNotFoundException, NamespaceException  {
    sourceService.checkSourceExists(sourceName);
    SourceFilePath filePath = SourceFilePath.fromURLPath(sourceName, path);
    FileFormat fileFormat;
    try {
      final PhysicalDatasetConfig physicalDatasetConfig = sourceService.getFilesystemPhysicalDataset(sourceName, filePath);
      fileFormat = FileFormat.getForFile(physicalDatasetConfig.getFormatSettings());
      fileFormat.setVersion(physicalDatasetConfig.getVersion());
    } catch (PhysicalDatasetNotFoundException nfe) {
      fileFormat = sourceService.getDefaultFileFormat(sourceName, filePath);
    }
    return new FileFormatUI(fileFormat, filePath);
  }

  @PUT
  @Path("/file_format/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public FileFormatUI saveFormatSettings(FileFormat fileFormat, @PathParam("path") String path)
      throws NamespaceException, SourceNotFoundException {
    SourceFilePath filePath = SourceFilePath.fromURLPath(sourceName, path);
    sourceService.checkSourceExists(filePath.getSourceName());
    fileFormat.setFullPath(filePath.toPathList());

    PhysicalDatasetConfig physicalDatasetConfig = new PhysicalDatasetConfig();
    physicalDatasetConfig.setName(filePath.getFileName().getName());
    physicalDatasetConfig.setFormatSettings(fileFormat.asFileConfig());
    physicalDatasetConfig.setVersion(fileFormat.getVersion());
    physicalDatasetConfig.setType(DatasetType.PHYSICAL_DATASET_SOURCE_FILE);
    physicalDatasetConfig.setFullPathList(filePath.toPathList());
    sourceService.createPhysicalDataset(filePath, physicalDatasetConfig);
    fileFormat.setVersion(physicalDatasetConfig.getVersion());
    return new FileFormatUI(fileFormat, filePath);
  }

  @POST
  @Path("/file_preview/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public JobDataFragment previewFileFormat(FileFormat fileFormat, @PathParam("path") String path)
      throws SourceFileNotFoundException, SourceNotFoundException {
    SourceFilePath filePath = SourceFilePath.fromURLPath(sourceName, path);
    return executor.previewPhysicalDataset(filePath.toString(), fileFormat);
  }

  @DELETE
  @Path("/file_format/{path: .*}")
  public void deleteFileFormat(@PathParam("path") String path,
                               @QueryParam("version") Long version) throws PhysicalDatasetNotFoundException {
    SourceFilePath filePath = SourceFilePath.fromURLPath(sourceName, path);
    if (version == null) {
      throw new ClientErrorException("missing version parameter");
    }

    Iterable<ReflectionGoal> reflections = reflectionService.get().getReflectionsByDatasetPath(new NamespaceKey(filePath.toPathList()));
    for (ReflectionGoal reflection : reflections) {
      reflectionService.get().remove(reflection);
    }

    sourceService.deletePhysicalDataset(sourceName, new PhysicalDatasetPath(filePath), version);
  }

  // format settings for folders.
  @GET
  @Path("/folder_format/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  public FileFormatUI getFolderFormat(@PathParam("path") String path)
      throws PhysicalDatasetNotFoundException, NamespaceException, SourceNotFoundException, IOException {
    SourceFolderPath folderPath = SourceFolderPath.fromURLPath(sourceName, path);
    sourceService.checkSourceExists(folderPath.getSourceName());

    FileFormat fileFormat;
    try {
      final PhysicalDatasetConfig physicalDatasetConfig = sourceService.getFilesystemPhysicalDataset(sourceName, folderPath);
      fileFormat = FileFormat.getForFolder(physicalDatasetConfig.getFormatSettings());
      fileFormat.setVersion(physicalDatasetConfig.getVersion());
    } catch (PhysicalDatasetNotFoundException nfe) {
      fileFormat = sourceService.getDefaultFileFormat(sourceName, folderPath, securityContext.getUserPrincipal().getName());
    }
    return new FileFormatUI(fileFormat, folderPath);
  }

  @POST
  @Path("/folder_preview/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public JobDataFragment previewFolderFormat(FileFormat fileFormat, @PathParam("path") String path)
    throws SourceFileNotFoundException, SourceNotFoundException {
    SourceFolderPath folderPath = SourceFolderPath.fromURLPath(sourceName, path);
    return executor.previewPhysicalDataset(folderPath.toString(), fileFormat);
  }

  @PUT
  @Path("/folder_format/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public FileFormatUI saveFolderFormat(FileFormat fileFormat, @PathParam("path") String path)
      throws NamespaceException, SourceNotFoundException {
    SourceFolderPath folderPath = SourceFolderPath.fromURLPath(sourceName, path);
    sourceService.checkSourceExists(folderPath.getSourceName());
    fileFormat.setFullPath(folderPath.toPathList());

    PhysicalDatasetConfig physicalDatasetConfig = new PhysicalDatasetConfig();
    physicalDatasetConfig.setName(folderPath.getFolderName().getName());
    physicalDatasetConfig.setFormatSettings(fileFormat.asFileConfig());
    physicalDatasetConfig.setType(DatasetType.PHYSICAL_DATASET_SOURCE_FOLDER);
    physicalDatasetConfig.setFullPathList(folderPath.toPathList());
    physicalDatasetConfig.setVersion(fileFormat.getVersion());
    sourceService.createPhysicalDataset(folderPath, physicalDatasetConfig);
    fileFormat.setVersion(physicalDatasetConfig.getVersion());
    return new FileFormatUI(fileFormat, folderPath);
  }

  @DELETE
  @Path("/folder_format/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteFolderFormat(@PathParam("path") String path,
                                 @QueryParam("version") Long version) throws PhysicalDatasetNotFoundException {
    if (version == null) {
      throw new ClientErrorException("missing version parameter");
    }

    SourceFolderPath folderPath = SourceFolderPath.fromURLPath(sourceName, path);
    Iterable<ReflectionGoal> reflections = reflectionService.get().getReflectionsByDatasetPath(new NamespaceKey(folderPath.toPathList()));
    for (ReflectionGoal reflection : reflections) {
      reflectionService.get().remove(reflection);
    }

    sourceService.deletePhysicalDataset(sourceName, new PhysicalDatasetPath(folderPath), version);
  }

  @POST
  @Path("new_untitled_from_file/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public InitialPreviewResponse createUntitledFromSourceFile(@PathParam("path") String path)
    throws DatasetNotFoundException, DatasetVersionNotFoundException, NamespaceException, NewDatasetQueryException {
    return datasetsResource.createUntitledFromSourceFile(sourceName, path);
  }

  @POST
  @Path("new_untitled_from_folder/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public InitialPreviewResponse createUntitledFromSourceFolder(@PathParam("path") String path)
    throws DatasetNotFoundException, DatasetVersionNotFoundException, NamespaceException, NewDatasetQueryException {
    return datasetsResource.createUntitledFromSourceFolder(sourceName, path);
  }

  @POST
  @Path("new_untitled_from_physical_dataset/{path: .*}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public InitialPreviewResponse createUntitledFromPhysicalDataset(@PathParam("path") String path)
    throws DatasetNotFoundException, DatasetVersionNotFoundException, NamespaceException, NewDatasetQueryException {
    return datasetsResource.createUntitledFromPhysicalDataset(sourceName, path);
  }

}
