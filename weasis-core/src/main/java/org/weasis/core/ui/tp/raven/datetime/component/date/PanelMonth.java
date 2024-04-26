/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.tp.raven.datetime.component.date;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Component;
import java.text.DateFormatSymbols;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

/* PanelMonth is a class that provides a panel for selecting a month in the DatePicker.
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/swing-datetime-picker">swing-datetime-picker</a>
 */
public class PanelMonth extends JPanel {

  private final EventMonthChanged monthChanged;
  private final DateSelection dateSelection;
  private final int month;
  private final int year;

  public PanelMonth(
      EventMonthChanged monthChanged, DateSelection dateSelection, int month, int year) {
    this.monthChanged = monthChanged;
    this.dateSelection = dateSelection;
    this.month = month;
    this.year = year;
    init();
  }

  private void init() {
    putClientProperty(FlatClientProperties.STYLE, "background:null");
    setLayout(
        new MigLayout("wrap 3,insets 3,fillx,gap 3,al center center", "fill,sg main", "fill"));
    int count = 12;
    for (int i = 0; i < count; i++) {
      final int month = i;
      ButtonMonthYear button = new ButtonMonthYear(dateSelection, i, false);
      button.setText(DateFormatSymbols.getInstance().getMonths()[i]);
      if (checkSelected(month + 1)) {
        button.setSelected(true);
      }
      button.addActionListener(
          e -> {
            monthChanged.monthSelected(month);
          });
      add(button);
    }
  }

  protected boolean checkSelected(int month) {
    if (dateSelection.dateSelectionMode == DatePicker.DateSelectionMode.SINGLE_DATE_SELECTED) {
      return dateSelection.getDate() != null
          && year == dateSelection.getDate().getYear()
          && month == dateSelection.getDate().getMonth();
    } else {
      return (dateSelection.getDate() != null
              && year == dateSelection.getDate().getYear()
              && month == dateSelection.getDate().getMonth())
          || (dateSelection.getToDate() != null
              && year == dateSelection.getToDate().getYear()
              && month == dateSelection.getToDate().getMonth());
    }
  }

  protected void checkSelection() {
    for (int i = 0; i < getComponentCount(); i++) {
      Component com = getComponent(i);
      if (com instanceof ButtonMonthYear) {
        ButtonMonthYear button = (ButtonMonthYear) com;
        button.setSelected(checkSelected(button.getValue() + 1));
      }
    }
  }

  public interface EventMonthChanged {

    void monthSelected(int month);
  }
}
