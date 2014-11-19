package com.jakewharton.sdkmanager

import com.jakewharton.sdkmanager.internal.PackageResolver
import com.jakewharton.sdkmanager.internal.SdkResolver
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.StopExecutionException

import java.util.concurrent.TimeUnit

import static com.jakewharton.sdkmanager.internal.Util.hasAndroidPlugin
import static com.jakewharton.sdkmanager.internal.Util.isOfflineBuild

/**
 * {@link SdkManagerAction} is the standalone
 * {@link org.gradle.api.Action} implementation in this plugin.
 */
class SdkManagerAction implements Action<Project>  {
  final Logger log = Logging.getLogger SdkManagerPlugin
  @Override
  void execute( Project project ) {
    if ( hasAndroidPlugin( project ) ) {
      throw new StopExecutionException(
      "Must be applied before 'android' or 'android-library' plugin." )
    }

    if ( isOfflineBuild( project ) ) {
      log.debug 'Offline build. Skipping package resolution.'
      return
    }

    // Eager resolve the SDK and local.properties pointer.
    def sdk
    time "SDK resolve", {
      sdk = SdkResolver.resolve project
	}

	// Defer resolving SDK package dependencies until after the model is finalized.
    project.afterEvaluate {
      time "Package resolve", {
			  PackageResolver.resolve project, sdk
		  }
    }
  }

  /**
   * Executes task closure and logs the
   * time it took for it to run in nanoseconds.
   *
   * @param name the human readable name of the task.
   * @param task the task closure to run.
   */
  def time( String name, Closure task ) {
    long before = java.lang.System.nanoTime()
    task.run()
    long after = java.lang.System.nanoTime()
    long took = TimeUnit.NANOSECONDS.toMillis( after - before )
    log.info "$name took $took ms."
  }
}
