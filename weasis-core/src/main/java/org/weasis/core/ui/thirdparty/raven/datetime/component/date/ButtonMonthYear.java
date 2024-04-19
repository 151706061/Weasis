/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.thirdparty.raven.datetime.component.date;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.ColorFunctions;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ButtonMonthYear extends JButton {

  public int getValue() {
    return value;
  }

  private final DateSelection dateSelection;
  private final int value;
  private final boolean isYear;
  private boolean press;
  private boolean hover;

  public ButtonMonthYear(DateSelection dateSelection, int value, boolean isYear) {
    this.dateSelection = dateSelection;
    this.value = value;
    this.isYear = isYear;
    init();
  }

  private void init() {
    setContentAreaFilled(false);
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
              press = true;
            }
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            if (SwingUtilities.isLeftMouseButton(e)) {
              press = false;
            }
          }

          @Override
          public void mouseEntered(MouseEvent e) {
            hover = true;
          }

          @Override
          public void mouseExited(MouseEvent e) {
            hover = false;
          }
        });
    putClientProperty(
        FlatClientProperties.STYLE,
        ""
            + "margin:6,6,6,6;"
            + "selectedForeground:contrast($Component.accentColor,$Button.background,#fff);");
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    FlatUIUtils.setRenderingHints(g2);
    int border = UIScale.scale(6);
    int arc = UIScale.scale(10);
    int width = getWidth() - border;
    int height = getHeight() - border;
    int x = (getWidth() - width) / 2;
    int y = (getHeight() - height) / 2;
    g2.setColor(getColor());
    FlatUIUtils.paintComponentBackground(g2, x, y, width, height, 0, arc);
    g2.dispose();
    super.paintComponent(g);
  }

  protected Color getColor() {
    Color color =
        isSelected()
            ? UIManager.getColor("Component.accentColor")
            : FlatUIUtils.getParentBackground(this);
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
