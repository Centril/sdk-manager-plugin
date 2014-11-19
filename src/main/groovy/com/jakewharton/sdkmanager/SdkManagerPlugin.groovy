package com.jakewharton.sdkmanager

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * {@link SdkManagerPlugin} is the gradle plugin class.
 */
class SdkManagerPlugin implements Plugin<Project> {
  @Override void apply(Project project) {
    new SdkManagerAction().execute( project )
  }
}
