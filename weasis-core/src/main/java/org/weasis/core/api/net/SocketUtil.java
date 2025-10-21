/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.net.ServerSocketFactory;

/** Socket operations utility for finding available TCP ports. */
public final class SocketUtil {

  public static final int PORT_MIN = 1024;
  public static final int PORT_MAX = 65535;

  private static final int MAX_ATTEMPTS_MULTIPLIER = 500;
  private static final String LOCALHOST = "localhost";

  private static final SecureRandom RANDOM = new SecureRandom();

  private SocketUtil() {}

  public static boolean isPortAvailable(int port) {
    try (ServerSocket _ = createServerSocket(port)) {
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  public static SortedSet<Integer> findAvailablePorts(int numberOfPorts) {
    validatePortCount(numberOfPorts);

    var availablePorts = new TreeSet<Integer>();
    int maxAttempts = numberOfPorts + MAX_ATTEMPTS_MULTIPLIER;

    for (int attempt = 0;
        attempt < maxAttempts && availablePorts.size() < numberOfPorts;
        attempt++) {
      availablePorts.add(findAvailablePort());
    }

    if (availablePorts.size() != numberOfPorts) {
      throw new IllegalStateException(
          "Could not find %d TCP ports available in the range [%d, %d]"
              .formatted(numberOfPorts, PORT_MIN, PORT_MAX));
    }
    return availablePorts;
  }

  public static int findAvailablePort() {
    int portRange = PORT_MAX - PORT_MIN + 1;

    for (int attempt = 0; attempt < portRange; attempt++) {
      int port = generateRandomPort();
      if (isPortAvailable(port)) {
        return port;
      }
    }
    throw new IllegalStateException(
        "Could not find an available TCP port in the range [%d, %d] after %d attempts"
            .formatted(PORT_MIN, PORT_MAX, portRange));
  }

  private static ServerSocket createServerSocket(int port) throws Exception {
    return ServerSocketFactory.getDefault()
        .createServerSocket(port, 1, InetAddress.getByName(LOCALHOST));
  }

  private static int generateRandomPort() {
    return PORT_MIN + RANDOM.nextInt(PORT_MAX - PORT_MIN + 1);
  }

  private static void validatePortCount(int numberOfPorts) {
    if (numberOfPorts <= 0) {
      throw new IllegalArgumentException("Number of ports must be positive: " + numberOfPorts);
    }
  }
}
