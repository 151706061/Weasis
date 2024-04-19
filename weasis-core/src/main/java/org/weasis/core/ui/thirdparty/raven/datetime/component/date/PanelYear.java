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
import java.awt.Component;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

public class PanelYear extends JPanel {

  public static final int YEAR_CELL = 28;
  private final EventYearChanged yearChanged;
  private final DateSelection dateSelection;
  private final int year;

  public PanelYear(EventYearChanged yearChanged, DateSelection dateSelection, int year) {
    this.yearChanged = yearChanged;
    this.dateSelection = dateSelection;
    this.year = year;
    init();
  }

  private void init() {
    putClientProperty(FlatClientProperties.STYLE, "" + "background:null");
    setLayout(
        new MigLayout("wrap 4,insets 3,fillx,gap 3,al center center", "fill,sg main", "fill"));
    int count = YEAR_CELL;
    for (int i = 0; i < count; i++) {
      final int y = getStartYear(year) + i;
      ButtonMonthYear button = new ButtonMonthYear(dateSelection, y, true);
      button.setText(y + "");
      if (checkSelected(y)) {
        button.setSelected(true);
      }
      button.addActionListener(
          e -> {
            yearChanged.yearSelected(y);
          });
      add(button);
    }
    checkSelection();
  }

  private int getStartYear(int year) {
    int initYear = 1900;
    int currentYear = year;
    int yearsPerPage = YEAR_CELL;
    int yearsPassed = currentYear - initYear;
    int pages = yearsPassed / yearsPerPage;
    int startingYearOnPage = initYear + (pages * yearsPerPage);
    return startingYearOnPage;
  }

  protected boolean checkSelected(int year) {
    if (dateSelection.dateSelectionMode == DatePicker.DateSelectionMode.SINGLE_DATE_SELECTED) {
      return dateSelection.getDate() != null && year == dateSelection.getDate().getYear();
    } else {
      return (dateSelection.getDate() != null && year == dateSelection.getDate().getYear())
          || (dateSelection.getToDate() != null && year == dateSelection.getToDate().getYear());
    }
  }

  protected void checkSelection() {
    for (int i = 0; i < getComponentCount(); i++) {
      Component com = getComponent(i);
      if (com instanceof ButtonMonthYear) {
        ButtonMonthYear button = (ButtonMonthYear) com;
        button.setSelected(checkSelected(button.getValue()));
      }
    }
  }

  public int getYear() {
    return year;
  }

  public interface EventYearChanged {

    void yearSelected(int year);
  }
}
