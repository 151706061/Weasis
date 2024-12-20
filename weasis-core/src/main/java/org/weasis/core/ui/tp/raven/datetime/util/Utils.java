/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.tp.raven.datetime.util;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.ColorFunctions;
import java.awt.*;
import javax.swing.*;

public class Utils {

  public static Point adjustPopupLocation(JPopupMenu popupMenu, Component component) {
    Window window = SwingUtilities.getWindowAncestor(component);
    Insets frameInsets = window.getInsets();
    Dimension popupSize = popupMenu.getComponent().getPreferredSize();
    if (window == null) {
      return new Point(0, component.getHeight());
    }
    int frameWidth = window.getWidth() - (frameInsets.left + frameInsets.right);
    int frameHeight = window.getHeight() - (frameInsets.top + frameInsets.bottom);
    Point locationOnFrame =
        SwingUtilities.convertPoint(component, new Point(0, component.getHeight()), window);
    int bottomSpace = frameHeight - locationOnFrame.y - popupSize.height;
    int x =
        Math.max(
            Math.min(locationOnFrame.x, frameInsets.left + frameWidth - popupSize.width),
            frameInsets.left);
    int y =
        frameInsets.top + bottomSpace > 0
            ? locationOnFrame.y
            : Math.max(
                locationOnFrame.y - component.getHeight() - popupSize.height, frameInsets.top);
    return SwingUtilities.convertPoint(window, new Point(x, y), component);
  }

  public static Color getColor(Color color, boolean press, boolean hover) {
    if (press) {
      return FlatLaf.isLafDark()
          ? ColorFunctions.lighten(color, 0.1f)
          : ColorFunctions.darken(color, 0.1f);
    } else if (hover) {
      return FlatLaf.isLafDark()
          ? ColorFunctions.lighten(color, 0.03f)
          : ColorFunctions.darken(color, 0.03f);
    }
    return color;
  }
}
