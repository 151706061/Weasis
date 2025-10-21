/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Supplies request body content for HTTP operations.
 *
 * <p>Provides lazy content supply for efficient handling of large files or dynamically generated
 * content. Can be called multiple times (e.g., for retries) and should return a fresh instance each
 * time.
 *
 * @param <T> content type (typically {@link InputStream})
 */
public interface BodySupplier<T> {

  /**
   * Supplies the body content.
   *
   * @return body content
   * @throws IOException if an I/O error occurs
   */
  T get() throws IOException;

  /**
   * @return content length in bytes, or -1 if unknown
   */
  long length();

  static BodySupplier<InputStream> ofBytes(byte[] bytes) {
    return new BodySupplier<>() {
      @Override
      public InputStream get() {
        return new ByteArrayInputStream(bytes);
      }

      @Override
      public long length() {
        return bytes.length;
      }
    };
  }

  static BodySupplier<InputStream> ofBytes(byte[] bytes, int offset, int length) {
    return offset == 0 && length == bytes.length
        ? ofBytes(bytes)
        : new BodySupplier<>() {
          @Override
          public InputStream get() {
            return new ByteArrayInputStream(bytes, offset, length);
          }

          @Override
          public long length() {
            return length;
          }
        };
  }

  static BodySupplier<InputStream> ofString(String content) {
    return ofString(content, StandardCharsets.UTF_8);
  }

  static BodySupplier<InputStream> ofString(String content, Charset charset) {
    return ofBytes(content.getBytes(charset));
  }

  static BodySupplier<InputStream> ofPath(Path path) throws IOException {
    long size = Files.size(path);
    return new BodySupplier<>() {
      @Override
      public InputStream get() throws IOException {
        return Files.newInputStream(path);
      }

      @Override
      public long length() {
        return size;
      }
    };
  }

  static BodySupplier<InputStream> empty() {
    return ofBytes(new byte[0]);
  }
}
