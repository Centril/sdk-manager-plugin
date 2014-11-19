package com.jakewharton.sdkmanager.internal

/**
 * {@link Downloader} is an interface used to
 * download an android SDK to a specific destination.
 */
interface Downloader {
  /**
   * Download an android SDK to dest.
   *
   * @param dest the location to store SDK in.
   */
  void download(File dest)

  /**
   * The actual implementation of the {@link Downloader}.
   * Simply delegates to {@link SdkDownload}.
   */
  static final class Real implements Downloader {
    @Override void download(File dest) {
      SdkDownload.get().download(dest)
    }
  }
}
