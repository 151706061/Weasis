/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.tp.raven.datetime.component.time;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;

/* Header is a class that provides methods for creating the header of the TimeSpinner.
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/swing-datetime-picker">swing-datetime-picker</a>
 */
public class Header extends JComponent {

  private MigLayout layout;
  private final EventHeaderChanged headerChanged;
  private final DecimalFormat format = new DecimalFormat("00");

  private boolean isAm;

  private Color color;

  public void setOrientation(int orientation) {
    String c =
        orientation == SwingConstants.VERTICAL
            ? "pos b1.x2+rel 0.5al n n"
            : "pos 0.5al b1.y2+rel n n";
    amPmToolBar.setOrientation(orientation);
    layout.setComponentConstraints(amPmToolBar, c);
  }

  public void setHour(int hour) {
    buttonHour.setText(format.format(hour));
    if (amPmToolBar.isVisible()) {
      if (!buttonAm.isSelected() && !buttonPm.isSelected()) {
        buttonAm.setSelected(true);
        setAm(true);
      }
    }
  }

  public void setMinute(int minute) {
    buttonMinute.setText(format.format(minute));
  }

  public void setAm(boolean isAm) {
    this.isAm = isAm;
    if (isAm) {
      buttonAm.setSelected(true);
    } else {
      buttonPm.setSelected(true);
    }
    headerChanged.amPmChanged(isAm);
  }

  public void clearTime() {
    group.clearSelection();
    buttonHour.setText("--");
    buttonMinute.setText("--");
    buttonHour.setSelected(true);
  }

  public boolean isAm() {
    return isAm;
  }

  public void setHourSelect(boolean isHour) {
    if (isHour) {
      buttonHour.setSelected(true);
    } else {
      buttonMinute.setSelected(true);
    }
  }

  public void setUse24hour(boolean use24hour) {
    amPmToolBar.setVisible(!use24hour);
  }

  public Header(EventHeaderChanged headerChanged) {
    this.headerChanged = headerChanged;
    init();
  }

  private void init() {
    setOpaque(true);
    layout = new MigLayout("fill,insets 10", "center");
    setLayout(layout);
    add(createToolBar(), "id b1");
    add(createAmPm(), "pos b1.x2+rel 0.5al n n");
  }

  protected JToolBar createToolBar() {
    JToolBar toolBar = new JToolBar();
    toolBar.putClientProperty(
        FlatClientProperties.STYLE, "background:null;" + "hoverButtonGroupBackground:null");
    buttonHour = createButton();
    buttonMinute = createButton();
    ButtonGroup group = new ButtonGroup();
    group.add(buttonHour);
    group.add(buttonMinute);
    buttonHour.setSelected(true);
    buttonHour.addActionListener(e -> headerChanged.hourMinuteChanged(true));
    buttonMinute.addActionListener(e -> headerChanged.hourMinuteChanged(false));
    toolBar.add(buttonHour);
    toolBar.add(createSplit());
    toolBar.add(buttonMinute);
    return toolBar;
  }

  protected JToggleButton createButton() {
    JToggleButton button = new JToggleButton("--");
    button.putClientProperty(
        FlatClientProperties.STYLE,
        "font:+15;"
            + "toolbar.margin:3,5,3,5;"
            + "foreground:contrast($Component.accentColor,$ToggleButton.background,#fff);"
            + "background:null;"
            + "toolbar.hoverBackground:null");
    return button;
  }

  protected JToggleButton createAmPmButton(String text) {
    JToggleButton button = new JToggleButton(text);
    button.addActionListener(
        e -> {
          boolean am = text.equals("AM");
          if (isAm != am) {
            isAm = am;
            headerChanged.amPmChanged(am);
          }
        });
    button.putClientProperty(
        FlatClientProperties.STYLE,
        "font:+1;"
            + "foreground:contrast($Component.accentColor,$ToggleButton.background,#fff);"
            + "background:null;"
            + "toolbar.hoverBackground:null");
    return button;
  }

  protected JLabel createSplit() {
    JLabel label = new JLabel(":");
    label.putClientProperty(
        FlatClientProperties.STYLE,
        "font:+10;" + "foreground:contrast($Component.accentColor,$Label.background,#fff)");
    return label;
  }

  protected JToolBar createAmPm() {
    amPmToolBar = new JToolBar();
    amPmToolBar.setOrientation(SwingConstants.VERTICAL);
    amPmToolBar.putClientProperty(
        FlatClientProperties.STYLE, "background:null;" + "hoverButtonGroupBackground:null");
    group = new ButtonGroup();
    buttonAm = createAmPmButton("AM");
    buttonPm = createAmPmButton("PM");
    group.add(buttonAm);
    group.add(buttonPm);
    amPmToolBar.add(buttonAm);
    amPmToolBar.add(buttonPm);
    return amPmToolBar;
  }

  /**
   * Override this method to paint the background color Do not use the component background because
   * the background reset while change themes
   */
  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    Color color = this.color;
    if (color == null) {
      color = UIManager.getColor("Component.accentColor");
    }
    g2.setColor(color);
    g2.fill(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
    g2.dispose();
    super.paintComponent(g);
  }

  public void setColor(Color color) {
    this.color = color;
  }

  /**
   * Override this method to return the background color to the JToolBar When JToolBar use null
   * background, so it will paint the parent background.
   */
  @Override
  public Color getBackground() {
    if (color != null) {
      return color;
    }
    return UIManager.getColor("Component.accentColor");
  }

  private JToggleButton buttonHour;
  public JToggleButton buttonMinute;

  public JToolBar amPmToolBar;
  public ButtonGroup group;
  public JToggleButton buttonAm;
  private JToggleButton buttonPm;

  protected interface EventHeaderChanged {

    void hourMinuteChanged(boolean isHour);

    void amPmChanged(boolean isAm);
  }
}
