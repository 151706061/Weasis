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

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class URIUtils {

  public static URI getURI(String pathOrUri) throws URISyntaxException {
    try {
      return new URI(pathOrUri);
    } catch (URISyntaxException e) {
      // Try to convert Windows file path to URI
      try {
        return Paths.get(pathOrUri).toUri();
      } catch (Exception ex) {
        // Ignore
      }
      throw e;
    }
  }

  public static boolean isHttpURI(URI uri) {
    return isProtocol(uri, "http") || isProtocol(uri, "https"); // $NON-NLS
  }

  public static boolean isFileURI(URI uri) {
    return isProtocol(uri, "file")
        || (uri.getScheme() == null && uri.getPath() != null); // $NON-NLS
  }

  public static boolean isProtocol(URI uri, String protocol) {
    return protocol.equalsIgnoreCase(uri.getScheme());
  }

  public static Path getAbsolutePath(URI uri) {
    if (isFileURI(uri)) {
      String path = uri.getPath();
      if (path != null) {
        return Paths.get(path).toAbsolutePath();
      }
    }
    return null;
  }
}
