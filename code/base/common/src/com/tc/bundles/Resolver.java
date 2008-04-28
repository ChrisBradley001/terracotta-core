/*
 * All content copyright (c) 2003-2007 Terracotta, Inc., except as may otherwise be noted in a separate copyright
 * notice. All rights reserved.
 */
package com.tc.bundles;

import org.apache.commons.io.FileUtils;
import org.osgi.framework.BundleException;

import com.tc.bundles.exception.BundleSpecException;
import com.tc.bundles.exception.BundleExceptionSummary;
import com.tc.bundles.exception.UnreadableBundleException;
import com.tc.bundles.exception.MissingBundleException;
import com.tc.logging.CustomerLogging;
import com.tc.logging.TCLogger;
import com.tc.logging.TCLogging;
import com.tc.properties.TCProperties;
import com.tc.properties.TCPropertiesImpl;
import com.terracottatech.config.Module;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Stack;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Resolver {

  private static final String   BUNDLE_VERSION        = "Bundle-Version";
  private static final String   BUNDLE_SYMBOLICNAME   = "Bundle-SymbolicName";

  private static final String   TC_PROPERTIES_SECTION = "l1.modules";

  private static final TCLogger logger                = TCLogging.getLogger(Resolver.class);

  // List of File where each is a repository root
  private final List            repositories          = new ArrayList();

  // List of Entry objects describing already resolved bundles
  private final List            registry              = new ArrayList();

  /**
   * Create a resolver for resolving modules from a set of repositories
   * 
   * @param repositoryStrings Each repository string can be either a file path or a file:// URL
   */
  public Resolver(final String[] repositoryStrings) throws BundleException {
    injectDefaultRepositories();

    for (int i = 0; i < repositoryStrings.length; i++) {
      String repository = repositoryStrings[i];
      File repoFile = resolveRepositoryLocation(repository);
      if (repoFile != null) {
        repositories.add(repoFile);
      }
    }

    if (repositories.isEmpty()) {
      String msg = "No module repositories have been specified via the com.tc.l1.modules.repositories system property";
      throw new RuntimeException(msg);
    }
  }

  public static String[] urlsToStrings(URL[] urls) {
    String[] strs = new String[urls.length];
    for (int i = 0; i < urls.length; i++) {
      File f = FileUtils.toFile(urls[i]);
      if (f != null) {
        strs[i] = f.getAbsolutePath();
      } else {
        strs[i] = urls[i].toExternalForm();
      }
    }
    return strs;
  }

  private void injectDefaultRepositories() throws BundleException {
    final String installRoot = System.getProperty("tc.install-root");
    if (installRoot != null) {
      final File defaultRepository = new File(installRoot, "modules");
      consoleLogger.debug("Appending default TIM repository: '" + defaultRepository.toString() + "'");
      repositories.add(defaultRepository);
    }

    final TCProperties props = TCPropertiesImpl.getProperties().getPropertiesFor(TC_PROPERTIES_SECTION);
    final String reposProp = props != null ? props.getProperty("repositories", true) : null;
    if (reposProp != null && reposProp.length() > 0) {
      final String[] entries = reposProp.split(",");
      if (entries != null) {
        for (int i = 0; i < entries.length; i++) {
          String entry = entries[i].trim();
          if (entry != null && entry.length() > 0) {
            consoleLogger.debug("Prepending default TIM repository: '" + entry.toString() + "'");
            repositories.add(resolveRepositoryLocation(entry));
          }
        }
      }
    }
  }

  /**
   * Resolve string as repository location - try to understand as both file path and as URL.
   * 
   * @param repository Repository file path or URL
   */
  static File resolveRepositoryLocation(String repository) throws BundleException {
    if (repository == null) throw new BundleException("Cannot process null repository location");

    // Try as file
    File file = new File(repository);
    if (file.exists() && file.isDirectory()) { return file; }

    // Try as URL
    URL url = null;
    try {
      url = new URL(repository);
    } catch (MalformedURLException e) {
      // handle later
    }

    if (url != null) {
      if (!url.getProtocol().equalsIgnoreCase("file")) {
        final String msg = formatMessage(Message.WARN_REPOSITORY_PROTOCOL_UNSUPPORTED,
                                         new Object[] { canonicalPath(url) });
        throw new BundleException(msg);
      }

      // Deprecated but allowed file URL
      file = FileUtils.toFile(url);
      if (file.exists() && file.isDirectory()) {
        consoleLogger.warn("Repository location defined as a URL: '" + repository
                           + "'.  This usage is deprecated and will be removed in the future.");
        return file;
      } else {
        consoleLogger.debug("Repository URL does not exist or is not a directory: '" + repository + "'...skipping.");
      }
    } else {
      consoleLogger.debug("Repository location does not exist or is not a directory: '" + repository + "'...skipping.");
    }

    return null;
  }

  public final File resolve(Module module) throws BundleException {
    final String name = module.getName();
    final String version = module.getVersion();
    final String groupId = module.getGroupId();

    // CDV-691: If you are defining a module in the tc-config.xml, the schema requires that you specify
    // a name and version, so this will never happen (although version could still be invalid).
    // But if you define programmatically in a TIM or in a test, it is possible to screw this up.
    if (name == null || version == null) {
      String msg = "Invalid module specification (name and version are required): name=" + name + ", version="
                   + version + ", groupId=" + groupId;
      throw new BundleException(msg);
    }

    try {
      final File location = resolveLocation(name, version, groupId);
      if (location == null) {
        final String msg = formatMessage(Message.ERROR_BUNDLE_UNRESOLVED, new Object[] { name, version, groupId });
        throw new MissingBundleException(msg, groupId, name, version, repositories, null);
      }
      logger.info("Resolved TIM " + groupId + ":" + name + ":" + version + " from " + location);
      DependencyStack dependencyStack = new DependencyStack();
      dependencyStack.push(module.getGroupId(), module.getName(), module.getVersion());
      resolveDependencies(location, dependencyStack);
      // printDependencyStack(dependencyStack, 0, 4, System.out);
      return location;
    } catch (BundleException e) {
      if (e instanceof BundleExceptionSummary) fatal(((BundleExceptionSummary) e).getSummary());
      throw e;
    }
  }

  public final File[] resolve(Module[] modules) throws BundleException {
    resolveDefaultModules();
    resolveAdditionalModules();

    for (int i = 0; (modules != null) && (i < modules.length); i++)
      resolve(modules[i]);

    return getResolvedFiles();
  }

  public final File[] getResolvedFiles() {
    int j = 0;
    final File[] files = new File[registry.size()];
    for (Iterator i = registry.iterator(); i.hasNext();) {
      final Entry entry = (Entry) i.next();
      files[j++] = entry.getLocation();
    }
    return files;
  }

  private Collection findJars(File rootLocation, String groupId, String name, String version) {
    final Collection jars = new ArrayList();
    String root = rootLocation.getPath();
    File mavenLocation = new File(OSGiToMaven.makeBundlePathname(root, groupId, name, version));
    File flatLocation = new File(OSGiToMaven.makeFlatBundlePathname(root, name, version, false));

    // expect to find TIM jar file in a Maven-like organized directory
    // using the TIM name as-is and a (possibly) massaged the version number...
    if (mavenLocation.isFile()) jars.add(mavenLocation); // find file in file://repo/../1.0.0-SNAPSHOT/

    // also collect the TIM jar file found at the top of the repository
    if (flatLocation.isFile()) jars.add(flatLocation); // find file in file://repo/

    // and return the list of jars
    return jars;
  }

  protected File resolveBundle(BundleSpec spec) {
    for (int i = repositories.size() - 1; i >= 0; i--) {
      final File root = (File) repositories.get(i);
      final File repository = new File(root, spec.getGroupId().replace('.', File.separatorChar));
      if (!repository.exists() || !repository.isDirectory()) {
        // This may not exist if using a flat repository structure, so don't warn about it
        continue;
      }

      final Collection jars = findJars(root, spec.getGroupId(), spec.getName(), spec.getVersion());
      for (final Iterator j = jars.iterator(); j.hasNext();) {
        final File bundleFile = (File) j.next();
        if (!bundleFile.isFile()) {
          warn(Message.WARN_FILE_IGNORED_INVALID_NAME, new Object[] { bundleFile.getName() });
          continue;
        }
        final Manifest manifest = getManifest(bundleFile);
        if (manifest == null) {
          warn(Message.WARN_FILE_IGNORED_MISSING_MANIFEST, new Object[] { bundleFile.getName() });
          continue;
        }
        final String symname = manifest.getMainAttributes().getValue(BUNDLE_SYMBOLICNAME);
        final String version = manifest.getMainAttributes().getValue(BUNDLE_VERSION);
        if (spec.isCompatible(symname, version)) { return bundleFile; }
      }
    }
    return null;
  }

  protected File resolveLocation(final String name, final String version, final String groupId) {
    final String symname = MavenToOSGi.artifactIdToSymbolicName(groupId, name);
    final String osgiVersionStr = MavenToOSGi.projectVersionToBundleVersion(version);
    Version osgiVersion = Version.parse(osgiVersionStr);

    if (logger.isDebugEnabled()) logger.debug("Resolving location of " + groupId + ":" + name + ":" + version);

    for (int i = repositories.size() - 1; i >= 0; i--) {
      File repositoryRoot = (File) repositories.get(i);
      final Collection jars = findJars(repositoryRoot, groupId, name, version);
      for (final Iterator j = jars.iterator(); j.hasNext();) {
        final File jar = (File) j.next();
        final Manifest manifest = getManifest(jar);
        if (isBundleMatch(jar, manifest, symname, osgiVersion)) { return addToRegistry(jar, manifest); }
      }

    }
    return null;
  }

  private boolean isBundleMatch(File jarFile, Manifest manifest, String bundleName, Version bundleVersion) {
    if (logger.isDebugEnabled()) logger.debug("Checking " + jarFile + " for " + bundleName + ":" + bundleVersion);

    // ignore bad JAR files
    if (manifest == null) return false;

    // found a match!
    if (BundleSpec.isMatchingSymbolicName(bundleName, manifest.getMainAttributes().getValue(BUNDLE_SYMBOLICNAME))) {
      final String manifestVersion = manifest.getMainAttributes().getValue(BUNDLE_VERSION);
      try {
        if (bundleVersion.equals(Version.parse(manifestVersion))) { return true; }
      } catch (NumberFormatException e) { // thrown by parseVersion()
        consoleLogger.warn("Bad version attribute in TIM manifest from jar file: '" + canonicalPath(jarFile)
                           + "', version='" + manifestVersion + "'.  Skipping...", e);
      }
    }
    return false;
  }

  private void resolveDefaultModules() throws BundleException {
    final TCProperties props = TCPropertiesImpl.getProperties().getPropertiesFor(TC_PROPERTIES_SECTION);
    final String defaultModulesProp = props != null ? props.getProperty("default") : null;

    if (defaultModulesProp == null) {
      consoleLogger.debug("No implicit modules were loaded because the l1.modules.default property is not set.");
      return;
    }

    final String[] defaultModulesSpec = BundleSpec.getRequirements(defaultModulesProp);
    if (defaultModulesSpec.length > 0) {
      for (int i = 0; i < defaultModulesSpec.length; i++) {
        BundleSpec spec = BundleSpec.newInstance(defaultModulesSpec[i]);
        DependencyStack dependencyStack = new DependencyStack();
        dependencyStack.push(spec.getSymbolicName(), spec.getVersion());
        ensureBundle(spec, dependencyStack);
        // printDependencyStack(dependencyStack, 0, 4, System.out);
      }
      return;
    }
    consoleLogger.debug("No implicit modules were loaded because the l1.modules.default property is empty.");
  }

  private static String canonicalPath(URL url) {
    File path = FileUtils.toFile(url);
    if (path == null) return url.toString();
    return canonicalPath(path);
  }

  private static String canonicalPath(File path) {
    try {
      return path.getCanonicalPath();
    } catch (IOException e) {
      return path.toString();
    }
  }

  private void resolveAdditionalModules() throws BundleException {
    final TCProperties props = TCPropertiesImpl.getProperties().getPropertiesFor(TC_PROPERTIES_SECTION);
    final String additionalModulesProp = props != null ? props.getProperty("additional") : null;
    if (additionalModulesProp == null) return;
    String[] additionalModulesSpec = BundleSpec.getRequirements(additionalModulesProp);
    for (int i = 0; i < additionalModulesSpec.length; i++) {
      BundleSpec spec = BundleSpec.newInstance(additionalModulesSpec[i]);
      DependencyStack dependencyStack = new DependencyStack();
      dependencyStack.push(spec.getSymbolicName(), spec.getVersion());
      ensureBundle(spec, dependencyStack);
      // printDependencyStack(dependencyStack, 0, 4, System.out);
    }
  }

  private BundleSpec[] getRequirements(Manifest manifest) {
    List requirementList = new ArrayList();
    String[] manifestRequirements = BundleSpec.getRequirements(manifest);
    for (int i = 0; i < manifestRequirements.length; i++) {
      requirementList.add(BundleSpec.newInstance(manifestRequirements[i]));
    }
    return (BundleSpec[]) requirementList.toArray(new BundleSpec[0]);
  }

  private void resolveDependencies(final File location, Stack dependencyStack) throws BundleException {
    final Manifest manifest = getManifest(location);
    if (manifest == null) {
      String msg = formatMessage(Message.ERROR_BUNDLE_UNREADABLE, new Object[] { location.getName(),
          canonicalPath(location.getParentFile()) });
      throw new UnreadableBundleException(msg, location);
    }
    final BundleSpec[] requirements = getRequirements(manifest);
    DependencyStack stack = (DependencyStack) dependencyStack.push(new DependencyStack());
    for (int i = 0; i < requirements.length; i++) {
      final BundleSpec spec = requirements[i];
      stack.push(spec.getSymbolicName(), spec.getVersion());
      try {
        ensureBundle(spec, stack);
      } catch (MissingBundleException e) {
        throw new MissingBundleException(e.getMessage(), spec.getGroupId(), spec.getName(), spec.getVersion(),
                                         repositories, dependencyStack);
      }
    }
    addToRegistry(location, manifest);
  }

  static void validateBundleSpec(final BundleSpec spec) throws BundleException {
    if (!spec.isVersionSpecified()) throw BundleSpecException.unspecifiedVersion(spec);
    if (!spec.isVersionSpecifiedAbsolute()) throw BundleSpecException.absoluteVersionRequired(spec);
  }

  private void ensureBundle(final BundleSpec spec, Stack dependencyStack) throws BundleException {
    validateBundleSpec(spec);
    File required = findInRegistry(spec);
    if (required == null) {
      required = resolveBundle(spec);
      if (required == null) {
        String msg = formatMessage(Message.ERROR_BUNDLE_DEPENDENCY_UNRESOLVED, new Object[] { spec.getName(),
            spec.getVersion(), spec.getGroupId() });
        throw new MissingBundleException(msg);
      }
      addToRegistry(required, getManifest(required));
      resolveDependencies(required, dependencyStack);
    }
  }

  private File addToRegistry(final File location, final Manifest manifest) {
    final Entry entry = new Entry(location, manifest);
    if (!registry.contains(entry)) registry.add(entry);
    return entry.getLocation();
  }

  private File findInRegistry(BundleSpec spec) {
    File location = null;
    for (Iterator i = registry.iterator(); i.hasNext();) {
      final Entry entry = (Entry) i.next();
      if (spec.isCompatible(entry.getSymbolicName(), entry.getVersion())) {
        location = entry.getLocation();
        break;
      }
    }
    return location;
  }

  private Manifest getManifest(final File file) {
    try {
      return getManifest(file.toURI().toURL());
    } catch (MalformedURLException e) {
      return null;
    }
  }

  private Manifest getManifest(final URL location) {
    try {
      final JarFile bundle = new JarFile(FileUtils.toFile(location));
      return bundle.getManifest();
    } catch (IOException e) {
      return null;
    }
  }

  private String warn(final Message message, final Object[] arguments) {
    final String msg = formatMessage(message, arguments);
    logger.warn(msg);
    return msg;
  }

  private String fatal(final String msg) {
    if (msg != null) consoleLogger.fatal(msg);
    return msg;
  }

  private static String formatMessage(final Message message, final Object[] arguments) {
    return MessageFormat.format(resourceBundle.getString(message.key()), arguments);
  }

  private final class Entry {
    private File     location;
    private Manifest manifest;

    public Entry(final File location, final Manifest manifest) {
      this.location = location;
      this.manifest = manifest;
    }

    public String getVersion() {
      return manifest.getMainAttributes().getValue(BUNDLE_VERSION);
    }

    public String getSymbolicName() {
      return manifest.getMainAttributes().getValue(BUNDLE_SYMBOLICNAME);
    }

    public File getLocation() {
      return location;
    }

    public boolean equals(Object object) {
      if (this == object) return true;
      if (!(object instanceof Entry)) return false;
      final Entry entry = (Entry) object;
      return location.equals(entry.getLocation()) && getVersion().equals(entry.getVersion())
             && getSymbolicName().equals(entry.getSymbolicName());
    }

    private static final int SEED1 = 18181;
    private static final int SEED2 = 181081;

    public int hashCode() {
      int result = SEED1;
      result = hash(result, this.location);
      result = hash(result, this.manifest);
      return result;
    }

    private int hash(int seed, int value) {
      return SEED2 * seed + value;
    }

    private int hash(int seed, Object object) {
      int result = seed;
      if (object == null) {
        result = hash(result, 0);
      } else {
        result = hash(result, object);
      }
      return result;
    }
  }

  private final class DependencyStack extends Stack {

    public void push(String groupId, String artifactId, String version) {
      StringBuffer buf = new StringBuffer(artifactId);
      buf.append(" version ");
      buf.append(OSGiToMaven.bundleVersionToProjectVersion(version)).append(" (");
      if (groupId.length() > 0) buf.append("group-id: ").append(groupId).append(", ");
      buf.append("file: ").append(OSGiToMaven.makeBundleFilename(artifactId, version, false)).append(")");
      push(buf.toString());
    }

    public void push(String symbolicName, String version) {
      push(OSGiToMaven.groupIdFromSymbolicName(symbolicName), OSGiToMaven.artifactIdFromSymbolicName(symbolicName),
           version);
    }
  }

  private static final class Message {

    static final Message WARN_FILE_IGNORED_INVALID_NAME       = new Message("warn.file.ignored.invalid-name");
    static final Message WARN_FILE_IGNORED_MISSING_MANIFEST   = new Message("warn.file.ignored.missing-manifest");
    static final Message WARN_REPOSITORY_PROTOCOL_UNSUPPORTED = new Message("warn.repository.protocol.unsupported");
    static final Message ERROR_BUNDLE_UNREADABLE              = new Message("error.bundle.unreadable");
    static final Message ERROR_BUNDLE_UNRESOLVED              = new Message("error.bundle.unresolved");
    static final Message ERROR_BUNDLE_DEPENDENCY_UNRESOLVED   = new Message("error.bundle-dependency.unresolved");

    private final String resourceBundleKey;

    private Message(final String resourceBundleKey) {
      this.resourceBundleKey = resourceBundleKey;
    }

    String key() {
      return resourceBundleKey;
    }
  }

  private static final TCLogger       consoleLogger = CustomerLogging.getConsoleLogger();
  private static final ResourceBundle resourceBundle;

  static {
    try {
      resourceBundle = ResourceBundle.getBundle(Resolver.class.getName(), Locale.getDefault(), Resolver.class
          .getClassLoader());
    } catch (MissingResourceException mre) {
      throw new RuntimeException("No resource bundle exists for " + Resolver.class.getName());
    } catch (Throwable t) {
      throw new RuntimeException("Unexpected error loading resource bundle for " + Resolver.class.getName(), t);
    }
  }
}
