/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net.auth;

import com.github.scribejava.core.httpclient.HttpClientConfig;
import java.net.ProxySelector;
import org.weasis.core.api.net.NetworkUtil;

public class JavaNetHttpClientConfig implements HttpClientConfig {

  private final int connectTimeout;
  private final int readTimeout;

  public JavaNetHttpClientConfig() {
    this.connectTimeout = NetworkUtil.getUrlConnectionTimeout();
    this.readTimeout = NetworkUtil.getUrlReadTimeout();
  }

  public int getConnectTimeout() {
    return connectTimeout;
  }

  public int getReadTimeout() {
    return readTimeout;
  }

  public ProxySelector getProxy() {
    return ProxySelector.getDefault();
  }

  @Override
  public HttpClientConfig createDefaultConfig() {
    return new JavaNetHttpClientConfig();
  }
}
