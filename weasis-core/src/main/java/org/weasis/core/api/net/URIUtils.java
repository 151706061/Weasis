/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import com.formdev.flatlaf.util.SystemInfo;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import org.weasis.core.util.StringUtil;

/** URI operations utility for conversions, protocol checks, and path extraction. */
public final class URIUtils {

  private URIUtils() {}

  public static URI getURI(String pathOrUri) throws URISyntaxException {
    Objects.requireNonNull(pathOrUri, "Path or URI cannot be null");
    try {
      return new URI(pathOrUri);
    } catch (URISyntaxException e) {
      return convertPathToURI(pathOrUri, e);
    }
  }

  private static URI convertPathToURI(String path, URISyntaxException originalException)
      throws URISyntaxException {
    try {
      return Paths.get(path).toUri();
    } catch (Exception ex) {
      originalException.addSuppressed(ex);
      throw originalException;
    }
  }

  public static boolean isHttpURI(URI uri) {
    return isProtocol(uri, "http") || isProtocol(uri, "https");
  }

  public static boolean isFileURI(URI uri) {
    return isProtocol(uri, "file") || isSchemelessPath(uri);
  }

  private static boolean isSchemelessPath(URI uri) {
    return uri.getScheme() == null && uri.getPath() != null;
  }

  public static boolean isProtocol(URI uri, String protocol) {
    if (uri == null || !StringUtil.hasText(protocol)) {
      throw new IllegalArgumentException("URI and protocol cannot be null");
    }
    return protocol.equalsIgnoreCase(uri.getScheme());
  }

  /**
   * Converts a file URI to a Path, handling UNC paths (e.g. file://wsl.localhost/... or
   * file://server/share/...).
   *
   * @param uri the URI to convert
   * @return the corresponding Path
   */
  public static Path toPath(URI uri) {
    Objects.requireNonNull(uri, "URI cannot be null");
    if (uri.getAuthority() != null && SystemInfo.isWindows) {
      // UNC path (e.g. file://server/share/...)
      return Path.of("\\\\" + uri.getAuthority() + uri.getPath());
    }
    return Paths.get(uri);
  }

  /**
   * Converts a file URI to a File, handling UNC paths (e.g. file://wsl.localhost/... or
   * file://server/share/...).
   *
   * @param uri the URI to convert
   * @return the corresponding File
   */
  public static File toFile(URI uri) {
    return toPath(uri).toFile();
  }

  public static Path getAbsolutePath(URI uri) {
    Objects.requireNonNull(uri, "URI cannot be null");

    if (!isFileURI(uri)) {
      return null;
    }
    return toPath(uri).toAbsolutePath();
  }
}
