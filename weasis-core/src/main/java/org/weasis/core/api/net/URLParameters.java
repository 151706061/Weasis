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

import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** HTTP connection parameters for URL requests including headers, timeouts, and caching. */
public record URLParameters(
    Map<String, String> headers,
    long ifModifiedSince,
    int connectTimeout,
    int readTimeout,
    boolean httpPost,
    boolean useCaches,
    boolean allowUserInteraction) {

  public static final URLParameters DEFAULT = new URLParameters();

  public URLParameters() {
    this(Collections.emptyMap());
  }

  public URLParameters(Map<String, String> headers) {
    this(headers, false);
  }

  public URLParameters(Map<String, String> headers, boolean httpPost) {
    this(
        headers,
        0L,
        NetworkUtil.getUrlConnectionTimeout(),
        NetworkUtil.getUrlReadTimeout(),
        httpPost,
        true,
        false);
  }

  public URLParameters(Map<String, String> headers, int connectTimeout, int readTimeout) {
    this(headers, 0L, connectTimeout, readTimeout, false, true, false);
  }

  public URLParameters(
      Map<String, String> headers,
      long ifModifiedSince,
      int connectTimeout,
      int readTimeout,
      boolean httpPost,
      boolean useCaches,
      boolean allowUserInteraction) {

    validateTimeouts(connectTimeout, readTimeout);
    validateIfModifiedSince(ifModifiedSince);

    this.headers = headers == null ? Collections.emptyMap() : Map.copyOf(headers);
    this.ifModifiedSince = ifModifiedSince;
    this.connectTimeout = connectTimeout;
    this.readTimeout = readTimeout;
    this.httpPost = httpPost;
    this.useCaches = useCaches;
    this.allowUserInteraction = allowUserInteraction;
  }

  private static void validateTimeouts(int connectTimeout, int readTimeout) {
    if (connectTimeout < 0) {
      throw new IllegalArgumentException("Connect timeout cannot be negative: " + connectTimeout);
    }
    if (readTimeout < 0) {
      throw new IllegalArgumentException("Read timeout cannot be negative: " + readTimeout);
    }
  }

  private static void validateIfModifiedSince(long ifModifiedSince) {
    if (ifModifiedSince < 0) {
      throw new IllegalArgumentException(
          "If-Modified-Since cannot be negative: " + ifModifiedSince);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public Builder toBuilder() {
    return new Builder(this);
  }

  public static Map<String, String> splitParameter(URL url) {
    Objects.requireNonNull(url, "URL cannot be null");

    String query = url.getQuery();
    if (query == null || query.isEmpty()) {
      return Collections.emptyMap();
    }

    return parseQueryString(query);
  }

  public static Map<String, List<String>> splitMultipleValuesParameter(URL url) {
    Objects.requireNonNull(url, "URL cannot be null");

    String query = url.getQuery();
    if (query == null || query.isEmpty()) {
      return Collections.emptyMap();
    }

    return parseMultiValueQueryString(query);
  }

  private static Map<String, String> parseQueryString(String query) {
    Map<String, String> queryPairs = new LinkedHashMap<>();
    String[] pairs = query.split("&");
    for (String pair : pairs) {
      parsePair(pair, queryPairs);
    }

    return Map.copyOf(queryPairs);
  }

  private static void parsePair(String pair, Map<String, String> queryPairs) {
    int idx = pair.indexOf("=");
    String key = decodeUrlComponent(idx > 0 ? pair.substring(0, idx) : pair);
    String value =
        (idx > 0 && pair.length() > idx + 1) ? decodeUrlComponent(pair.substring(idx + 1)) : "";

    queryPairs.put(key, value);
  }

  private static Map<String, List<String>> parseMultiValueQueryString(String query) {
    Map<String, List<String>> queryPairs = new LinkedHashMap<>();
    String[] pairs = query.split("&");
    for (String pair : pairs) {
      parseMultiValuePair(pair, queryPairs);
    }

    return Map.copyOf(queryPairs);
  }

  private static void parseMultiValuePair(String pair, Map<String, List<String>> queryPairs) {
    int idx = pair.indexOf("=");
    String key = decodeUrlComponent(idx > 0 ? pair.substring(0, idx) : pair);
    String value =
        (idx > 0 && pair.length() > idx + 1) ? decodeUrlComponent(pair.substring(idx + 1)) : null;

    queryPairs.computeIfAbsent(key, k -> new LinkedList<>()).add(value);
  }

  private static String decodeUrlComponent(String component) {
    return URLDecoder.decode(component, StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return "URLParameters{"
        + "headers="
        + headers
        + ", ifModifiedSince="
        + ifModifiedSince
        + ", connectTimeout="
        + connectTimeout
        + ", readTimeout="
        + readTimeout
        + ", httpPost="
        + httpPost
        + ", useCaches="
        + useCaches
        + ", allowUserInteraction="
        + allowUserInteraction
        + '}';
  }

  public static final class Builder {

    private Map<String, String> headers = Collections.emptyMap();
    private long ifModifiedSince = 0L;
    private int connectTimeout = NetworkUtil.getUrlConnectionTimeout();
    private int readTimeout = NetworkUtil.getUrlReadTimeout();
    private boolean httpPost = false;
    private boolean useCaches = true;
    private boolean allowUserInteraction = false;

    Builder() {}

    Builder(URLParameters parameters) {
      this.headers = parameters.headers;
      this.ifModifiedSince = parameters.ifModifiedSince;
      this.connectTimeout = parameters.connectTimeout;
      this.readTimeout = parameters.readTimeout;
      this.httpPost = parameters.httpPost;
      this.useCaches = parameters.useCaches;
      this.allowUserInteraction = parameters.allowUserInteraction;
    }

    public Builder headers(Map<String, String> headers) {
      this.headers = headers;
      return this;
    }

    public Builder ifModifiedSince(long ifModifiedSince) {
      this.ifModifiedSince = ifModifiedSince;
      return this;
    }

    public Builder connectTimeout(int connectTimeout) {
      this.connectTimeout = connectTimeout;
      return this;
    }

    public Builder readTimeout(int readTimeout) {
      this.readTimeout = readTimeout;
      return this;
    }

    public Builder httpPost(boolean httpPost) {
      this.httpPost = httpPost;
      return this;
    }

    public Builder useCaches(boolean useCaches) {
      this.useCaches = useCaches;
      return this;
    }

    public Builder allowUserInteraction(boolean allowUserInteraction) {
      this.allowUserInteraction = allowUserInteraction;
      return this;
    }

    public URLParameters build() {
      return new URLParameters(
          headers,
          ifModifiedSince,
          connectTimeout,
          readTimeout,
          httpPost,
          useCaches,
          allowUserInteraction);
    }
  }
}
