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
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class URIUtilsTest {

  @Test
  void getURI_returnsURIForValidInput() throws URISyntaxException {
    URI uri = URIUtils.getURI("http://example.com/view?query=123");
    Assertions.assertEquals("http", uri.getScheme());
    Assertions.assertEquals("example.com", uri.getHost());

    Assertions.assertTrue(URIUtils.isHttpURI(uri));
    Assertions.assertNull(URIUtils.getAbsolutePath(uri));

    Assertions.assertThrows(URISyntaxException.class, () -> URIUtils.getURI("/path/to\0file.txt"));
  }

  @Test
  void isHttpURI_returnsTrueForHttpsScheme() throws URISyntaxException {
    URI uri = new URI("https://example.com");
    Assertions.assertTrue(URIUtils.isHttpURI(uri));
    Assertions.assertTrue(URIUtils.isProtocol(uri, "https"));
    Assertions.assertFalse(URIUtils.isFileURI(uri));
  }

  @Test
  void ftpURI() throws URISyntaxException {
    URI uri = new URI("ftp://example.com");
    Assertions.assertFalse(URIUtils.isHttpURI(uri));
    Assertions.assertFalse(URIUtils.isHttpURI(uri));
    Assertions.assertTrue(URIUtils.isProtocol(uri, "ftp"));
  }

  @Test
  void fileURI() throws URISyntaxException {
    URI uri = URIUtils.getURI("file:///path/to/file");
    Assertions.assertTrue(URIUtils.isFileURI(uri));
    Assertions.assertEquals("/path/to/file", URIUtils.getAbsolutePath(uri).toString());

    uri = URIUtils.getURI("file:/path/to/file");
    Assertions.assertTrue(URIUtils.isFileURI(uri));
    Assertions.assertEquals("/path/to/file", URIUtils.getAbsolutePath(uri).toString());

    uri = URIUtils.getURI("/path/to/file.txt");
    Assertions.assertTrue(URIUtils.isFileURI(uri));
    Assertions.assertEquals("/path/to/file.txt", URIUtils.getAbsolutePath(uri).toString());

    uri = URIUtils.getURI("path/to/file.txt");
    Assertions.assertTrue(URIUtils.isFileURI(uri));
    Assertions.assertEquals("path/to/file.txt", uri.getPath());
    Assertions.assertEquals(
        Paths.get("path/to/file.txt").toAbsolutePath().toString(),
        URIUtils.getAbsolutePath(uri).toString());

    uri = URIUtils.getURI("");
    Assertions.assertTrue(URIUtils.isFileURI(uri));

    uri = URIUtils.getURI("file:///C:/path/to/file.txt");
    Assertions.assertTrue(URIUtils.isFileURI(uri));

    // FIXME not platform independent
    //    Assertions.assertEquals("/C:/path/to/file.txt", uri.getPath());
    //    Assertions.assertEquals("/C:/path/to/file.txt", URIUtils.getAbsolutePath(uri).toString());

    uri = URIUtils.getURI("C:\\path\\to\\file.txt");
    Assertions.assertTrue(URIUtils.isFileURI(uri));
  }
}
