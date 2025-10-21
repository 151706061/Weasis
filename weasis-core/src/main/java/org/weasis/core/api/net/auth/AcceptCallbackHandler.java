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

import com.github.scribejava.core.oauth.OAuth20Service;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Optional;

/**
 * Handles OAuth2 callback server operations for asynchronous socket connections. Processes OAuth2
 * authorization code responses via the CompletionHandler interface.
 */
public interface AcceptCallbackHandler
    extends CompletionHandler<AsynchronousSocketChannel, AsyncCallbackServerHandler> {

  /**
   * Gets the OAuth2 authorization code received from the callback.
   *
   * @return an Optional containing the authorization code, or empty if not yet received
   */
  Optional<String> code();

  /**
   * Sets the OAuth2 authorization code received from the callback.
   *
   * @param code the authorization code (must not be null)
   */
  void code(String code);

  /**
   * Gets the OAuth2 service used for authentication.
   *
   * @return the OAuth2 service instance
   */
  OAuth20Service service();
}
