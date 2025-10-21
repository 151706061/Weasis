/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

/** URLConnection wrapper providing HttpStream compatibility with proper resource cleanup. */
public record ClosableURLConnection(URLConnection urlConnection) implements HttpStream {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClosableURLConnection.class);

  public ClosableURLConnection {
    Objects.requireNonNull(urlConnection, "URLConnection cannot be null");
  }

  @Override
  public void close() {
    if (urlConnection instanceof HttpURLConnection httpConnection) {
      httpConnection.disconnect();
    }
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return urlConnection.getInputStream();
  }

  @Override
  public int getResponseCode() {
    if (urlConnection instanceof HttpURLConnection httpConnection) {
      return getResponseCodeSafely(httpConnection);
    }
    return HttpURLConnection.HTTP_OK;
  }

  @Override
  public String getResponseMessage() {
    if (urlConnection instanceof HttpURLConnection httpConnection) {
      return getResponseMessageSafely(httpConnection);
    }
    return StringUtil.EMPTY_STRING;
  }

  @Override
  public String getHeaderField(String key) {
    return urlConnection.getHeaderField(key);
  }

  public OutputStream getOutputStream() throws IOException {
    return urlConnection.getOutputStream();
  }

  private int getResponseCodeSafely(HttpURLConnection httpConnection) {
    try {
      return httpConnection.getResponseCode();
    } catch (IOException e) {
      LOGGER.warn("Failed to retrieve HTTP response code", e);
      return HttpURLConnection.HTTP_OK;
    }
  }

  private String getResponseMessageSafely(HttpURLConnection httpConnection) {
    try {
      return httpConnection.getResponseMessage();
    } catch (IOException e) {
      LOGGER.warn("Failed to retrieve HTTP response message", e);
      return StringUtil.EMPTY_STRING;
    }
  }
}
