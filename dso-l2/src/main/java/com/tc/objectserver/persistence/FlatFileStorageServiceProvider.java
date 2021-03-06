/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.tc.objectserver.persistence;

import org.terracotta.entity.PlatformConfiguration;
import org.terracotta.entity.ServiceProvider;
import org.terracotta.entity.ServiceConfiguration;
import org.terracotta.entity.ServiceProviderCleanupException;
import org.terracotta.persistence.IPersistentStorage;

import com.tc.io.TCFile;
import com.tc.io.TCFileImpl;
import com.tc.io.TCRandomFileAccessImpl;
import com.tc.logging.CustomerLogging;
import com.tc.logging.TCLogger;
import com.tc.logging.TCLogging;
import com.tc.util.Assert;
import com.tc.util.NonBlockingStartupLock;
import com.tc.util.startuplock.FileNotCreatedException;
import com.tc.util.startuplock.LocationNotCreatedException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.HashSet;

import org.terracotta.entity.ServiceProviderConfiguration;


/**
 * This service provides a very simple key-value storage persistence system.  It allows key-value data to be serialized to
 * a file in the working directory using Java serialization.
 * 
 * The initial use was to test/support platform restart without depending on CoreStorage.
 */
public class FlatFileStorageServiceProvider implements ServiceProvider, Closeable {
  private static final TCLogger logger = TCLogging.getLogger(FlatFileStorageServiceProvider.class);
  private static final TCLogger consoleLogger = CustomerLogging.getConsoleLogger();


  private Path directory;
  private final Set<Long> consumers = new HashSet<>();
  private NonBlockingStartupLock lock;

  @Override
  public boolean initialize(ServiceProviderConfiguration configuration, PlatformConfiguration platformConfiguration) {
    // Currently, this provider is created directly so there is no chance of seeing any other kind of provider.
    // In the future, this may change.
    Assert.assertTrue(configuration instanceof FlatFileStorageProviderConfiguration);
    FlatFileStorageProviderConfiguration flatFileConfiguration = (FlatFileStorageProviderConfiguration)configuration;
    File targetDirectory = flatFileConfiguration.getBasedir();
    Assert.assertNotNull(targetDirectory);
    // We want to use a per-server directory (since this path is likely the same for the entire stripe).
    File singleServerDirectory = new File(targetDirectory, platformConfiguration.getServerName());
    // Ensure that we have the directory.
    if (!singleServerDirectory.isDirectory()) {
      // Make the directories.
      boolean didMakeDirectories = singleServerDirectory.mkdirs();
      // If this fails, throw an exception - not an assert, as this is a config issue.
      if (!didMakeDirectories) {
        throw new IllegalArgumentException("Restartable persistent directoy did not exist and could not be created: " + singleServerDirectory.getAbsolutePath());
      }
    }
    this.directory = singleServerDirectory.toPath();
    logger.info("Initialized flat file storage to: " + this.directory);
    
    // This service needs to ensure that another server instance isn't using the same top-level directory.
    // (note that we can call System.exit(1) if this fails - that is what the 4.x server did in the same situation)
    TCFile location = new TCFileImpl(targetDirectory);
    Assert.assertNull(this.lock);
    this.lock = new NonBlockingStartupLock(location, flatFileConfiguration.shouldBlockOnLock());
    try {
      if (!this.lock.canProceed(new TCRandomFileAccessImpl())) {
        consoleLogger.error("Another L2 process is using the directory " + location + " as data directory.");
        consoleLogger.error("Exiting...");
        System.exit(1);
      }
    } catch (LocationNotCreatedException e) {
      // Unexpected - fatal.
      Assert.fail(e.getLocalizedMessage());
    } catch (FileNotCreatedException e) {
      // Unexpected - fatal.
      Assert.fail(e.getLocalizedMessage());
    }
    return true;
  }

  @Override
  public <T> T getService(long consumerID, ServiceConfiguration<T> configuration) {
    consumers.add(consumerID);
    String filename = "consumer_" + consumerID + ".dat";
    File file = this.directory.resolve(filename).toFile();
    FlatFilePersistentStorage storage = new FlatFilePersistentStorage(file);
    return configuration.getServiceType().cast(storage);
  }

  @Override
  public Collection<Class<?>> getProvidedServiceTypes() {
    return Collections.singleton(IPersistentStorage.class);
  }

  @Override
  public void clear() throws ServiceProviderCleanupException {
    // check that either there are no consumers or platform is the only consumer
    Assert.assertTrue((consumers.size() == 0) || (consumers.size() == 1 && consumers.iterator().next() == 0));

    final String CONSUMER_FILE_PAT = "consumer_[0-9]+.dat";

    // remove data files
    for(File file : directory.toFile().listFiles()) {
      if(file.getName().matches(CONSUMER_FILE_PAT) && !file.delete()) {
        throw new ServiceProviderCleanupException("FlatFileStorageServiceProvider clear failed - can't delete " + file.getAbsolutePath());
      }
    }
  }

  @Override
  public void close() throws IOException {
    // When being tested on the passthrough server, we need to give up our lock file.
    // Do the null check since someone might close without init.
    if (null != this.lock) {
      this.lock.release();
    }
  }
}
