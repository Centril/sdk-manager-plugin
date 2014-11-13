package com.jakewharton.sdkmanager.internal

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import org.gradle.api.Project

/**
 * {@link Util} contains a set of utilities
 * for the {@link com.jakewharton.sdkmanager.SdkManagerPlugin}.
 */
class Util {
	/**
	 * Checks if a given {@link Project} has had an android plugin applied to it.
	 * This can be either the {@link AppPlugin} or {@link LibraryPlugin}.
	 *
	 * @param project the plugin to check.
	 * @return true if it had an android plugin applied to it, otherwise false.
	 */
	static def hasAndroidPlugin( Project project ) {
		return project.plugins.hasPlugin( AppPlugin ) || project.plugins.hasPlugin( LibraryPlugin )
	}

	/**
	 * Checks if the build is in an offline mode.
	 *
	 * @param project Any gradle {@link Project}.
	 * @return true if the build is being done offline.
	 */
	static def isOfflineBuild( Project project ) {
		return project.getGradle().getStartParameter().isOffline()
	}
}
