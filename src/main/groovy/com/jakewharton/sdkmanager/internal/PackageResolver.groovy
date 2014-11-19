package com.jakewharton.sdkmanager.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.StopExecutionException

import static com.android.SdkConstants.FD_BUILD_TOOLS
import static com.android.SdkConstants.FD_EXTRAS
import static com.android.SdkConstants.FD_M2_REPOSITORY
import static com.android.SdkConstants.FD_PLATFORMS
import static com.android.SdkConstants.FD_ADDONS
import static com.android.SdkConstants.FD_PLATFORM_TOOLS
import static com.jakewharton.sdkmanager.internal.Util.hasAndroidPlugin

/**
 * {@link PackageResolver} resolves, verifies and downloads (if missing)
 * the build tools (1), platform tools, compile version (1),
 * support libraries, google play services libraries.
 *
 * (1): only resolved if applied on a project on which
 * an android plugin (app/library) has been applied on.
 */
class PackageResolver {
  /**
   * Resolves packages for project with the android SDK expected at sdk.
   *
   * @param project The {@link Project} to resolve for.
   * @param sdk The android SDK path is expected in this path.
   */
  static void resolve(Project project, File sdk) {
    new PackageResolver(project, sdk, new AndroidCommand.Real(sdk, new System.Real())).resolve()
  }

  /**
   * Checks if the given folder exists and is non-empty.
   *
   * @param folder the folder to check.
   * @return true if it existed and was non-empty.
   */
  static boolean folderExists(File folder) {
    return folder.exists() && folder.list().length != 0
  }

  static final String GOOGLE_API_PREFIX = "Google Inc.:Google APIs:"
  static final String GOOGLE_GDK_PREFIX = "Google Inc.:Glass Development Kit Preview:"

  final Logger log = Logging.getLogger PackageResolver
  final Project project
  final File sdk
  final File buildToolsDir
  final File platformToolsDir
  final File platformsDir
  final File addonsDir
  final File androidRepositoryDir
  final File googleRepositoryDir
  final AndroidCommand androidCommand

  /**
   * Constructs a {@link PackageResolver} given a project to resolve for,
   * a android sdk path, and an {@link AndroidCommand} to use.
   *
   * @param project The {@link Project} to resolve for.
   * @param sdk The android SDK path is expected in this path.
   * @param androidCommand The {@link AndroidCommand} to use for running commands against the SDK.
   */
  PackageResolver(Project project, File sdk, AndroidCommand androidCommand) {
    this.sdk = sdk
    this.project = project
    this.androidCommand = androidCommand

    buildToolsDir = new File(sdk, FD_BUILD_TOOLS)
    platformToolsDir = new File(sdk, FD_PLATFORM_TOOLS)
    platformsDir = new File(sdk, FD_PLATFORMS)
    addonsDir = new File(sdk, FD_ADDONS)

    def extrasDir = new File(sdk, FD_EXTRAS)
    def androidExtrasDir = new File(extrasDir, 'android')
    androidRepositoryDir = new File(androidExtrasDir, FD_M2_REPOSITORY)
    def googleExtrasDir = new File(extrasDir, 'google')
    googleRepositoryDir = new File(googleExtrasDir, FD_M2_REPOSITORY)
  }

  /**
   * Resolves everything that can be resolved and skips what can't.
   * @see PackageResolver PackageResolver for notes about what is resolved when.
   */
  def resolve() {
    resolving('build-tools', true, this.&resolveBuildTools)
    resolving('platform-tools', false, this.&resolvePlatformTools)
    resolving('compile-version', true, this.&resolveCompileVersion)
    resolving('support-library', false, this.&resolveSupportLibraryRepository)
    resolving('play-services', false, this.&resolvePlayServiceRepository)
  }

  /**
   * Conditionally runs a "resolving" closure under name.
   * The closure is skipped if android was required,
   * but the android plugin wasn't applied.
   *
   * @param name The name to give the closure to run.
   * @param requireAndroid Whether or not to require that the
   *                       android plugin be applied for the closure to run.
   * @param closure The closure to run should the conditions for running be met.
   */
  def resolving( String name, boolean requireAndroid, Closure closure ) {
    if ( requireAndroid && !hasAndroidPlugin( project ) ) {
      log.debug "Skipping: $name, no android plugin detected"
    } else {
      log.debug "Resolving: $name"
      closure()
    }
  }

  /**
   * Resolves the build tools using the revision specified in android.buildToolsRevision.
   * If missing, the revision will be downloaded.
   */
  def resolveBuildTools() {
    def buildToolsRevision = project.android.buildToolsRevision
    log.debug "Build tools version: $buildToolsRevision"

    def buildToolsRevisionDir = new File(buildToolsDir, buildToolsRevision.toString())
    if (folderExists(buildToolsRevisionDir)) {
      log.debug 'Build tools found!'
      return
    }

    log.lifecycle "Build tools $buildToolsRevision missing. Downloading..."

    def code = androidCommand.update "build-tools-$buildToolsRevision"
    if (code != 0) {
      throw new StopExecutionException("Build tools download failed with code $code.")
    }
  }

  /**
   * Resolves the platform tools and downloads them if missing.
   */
  def resolvePlatformTools() {
    if (folderExists(platformToolsDir)) {
      log.debug 'Platform tools found!'
      return
    }

    log.lifecycle "Platform tools missing. Downloading..."

    def code = androidCommand.update "platform-tools"
    if (code != 0) {
      throw new StopExecutionException("Platform tools download failed with code $code.")
    }
  }

  /**
   * Resolves the compile version downloads it if missing.
   * Additionally, the google SDK is installed if missing.
   */
  def resolveCompileVersion() {
    String compileVersion = project.android.compileSdkVersion
    log.debug "Compile API version: $compileVersion"

    if (compileVersion.startsWith(GOOGLE_API_PREFIX)) {
      // The google SDK requires the base android SDK as a prerequisite, but
      // the SDK manager won't follow dependencies automatically.
      def baseVersion = compileVersion.replace(GOOGLE_API_PREFIX, "android-")
      installIfMissing(platformsDir, baseVersion)
      def addonVersion = compileVersion.replace(GOOGLE_API_PREFIX, "addon-google_apis-google-")
      installIfMissing(addonsDir, addonVersion);
    } else if (compileVersion.startsWith(GOOGLE_GDK_PREFIX)) {
      def gdkVersion = compileVersion.replace(GOOGLE_GDK_PREFIX, "addon-google_gdk-google-")
      installIfMissing(platformsDir, gdkVersion);
    } else {
      installIfMissing(platformsDir, compileVersion);
    }
  }

  /**
   * Installs compilation API version at baseDir if not already installed.
   *
   * @param baseDir where the compilation API versions are stored.
   * @param version the compilation API version that will be installed if needed.
   */
  def installIfMissing(baseDir, version) {
    def existingDir = new File(baseDir, version)
    if (folderExists(existingDir)) {
      log.debug "Compilation API $version found!"
      return
    }

    log.lifecycle "Compilation API $version missing. Downloading..."

    def code = androidCommand.update version
    if (code != 0) {
      throw new StopExecutionException("Compilation API $version download failed with code $code.")
    }
  }

  /**
   * Resolves the support libraries if the project
   * applied upon has one of them as a dependency.
   *
   * If missing, they will be downloaded, as well
   * as using that path as a local maven repository.
   */
  def resolveSupportLibraryRepository() {
    def supportLibraryDeps = findDependenciesWithGroup 'com.android.support'
    if (supportLibraryDeps.isEmpty()) {
      log.debug 'No support library dependency found.'
      return
    }

    log.debug "Found support library dependencies: $supportLibraryDeps"

    project.repositories.maven {
      url = androidRepositoryDir
    }

    def needsDownload = false;
    if (!folderExists(androidRepositoryDir)) {
      needsDownload = true
      log.lifecycle 'Support library repository missing. Downloading...'
    } else if (!dependenciesAvailable(supportLibraryDeps)) {
      needsDownload = true
      log.lifecycle 'Support library repository outdated. Downloading update...'
    }

    if (needsDownload) {
      def code = androidCommand.update 'extra-android-m2repository'
      if (code != 0) {
        throw new StopExecutionException("Support repository download failed with code $code.")
      }
    }
  }

  /**
   * Resolves the google play services if the project
   * applied upon has one of them as a dependency.
   *
   * If missing, they will be downloaded, as well
   * as using that path as a local maven repository.
   */
  def resolvePlayServiceRepository() {
    def playServicesDeps = findDependenciesWithGroup 'com.google.android.gms'
    if (playServicesDeps.isEmpty()) {
      log.debug 'No Google Play Services dependency found.'
      return
    }

    log.debug "Found Google Play Services dependencies: $playServicesDeps"

    project.repositories {
      maven {
        url = androidRepositoryDir
      }
      maven {
        url = googleRepositoryDir
      }
    }

    def needsDownload = false;
    if (!folderExists(googleRepositoryDir)) {
      needsDownload = true
      log.lifecycle 'Google Play Services repository missing. Downloading...'
    } else if (!dependenciesAvailable(playServicesDeps)) {
      needsDownload = true
      log.lifecycle 'Google Play Services repository outdated. Downloading update...'
    }

    if (needsDownload) {
      def code = androidCommand.update 'extra-google-m2repository'
      if (code != 0) {
        throw new StopExecutionException(
            "Google Play Services repository download failed with code $code.")
      }
    }
  }

  /**
   * Finds all dependencies in all configurations that has group.
   *
   * @param group The group to match on.
   * @return The list of dependencies.
   */
  def findDependenciesWithGroup(String group) {
    def deps = []
    for (Configuration configuration : project.configurations) {
      for (Dependency dependency : configuration.dependencies) {
        if (group.equals(dependency.group)) {
          deps.add dependency
        }
      }
    }
    return deps
  }

  /**
   * Checks if the dependencies given by deps
   * is actually available on the file system.
   *
   * @param deps The dependencies to check.
   * @return true if available.
   */
  def dependenciesAvailable(def deps) {
    try {
      project.configurations.detachedConfiguration(deps as Dependency[]).files
      return true
    } catch (Exception ignored) {
      return false
    }
  }
}
