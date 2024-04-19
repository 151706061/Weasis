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

public class DateSelection {

  protected DatePicker.DateSelectionMode dateSelectionMode =
      DatePicker.DateSelectionMode.SINGLE_DATE_SELECTED;
  private DateSelectionAble dateSelectionAble;
  private SingleDate date;
  private SingleDate toDate;
  private SingleDate hoverDate;

  protected final DatePicker datePicker;

  protected DateSelection(DatePicker datePicker) {
    this.datePicker = datePicker;
  }

  public SingleDate getDate() {
    return date;
  }

  public void setDate(SingleDate date) {
    if (!checkSelection(date)) {
      return;
    }
    this.date = date;
    datePicker.runEventDateChanged();
  }

  public SingleDate getToDate() {
    return toDate;
  }

  public void setToDate(SingleDate toDate) {
    if (!checkSelection(toDate)) {
      return;
    }
    this.toDate = toDate;
  }

  public SingleDate getHoverDate() {
    return hoverDate;
  }

  public void setHoverDate(SingleDate hoverDate) {
    this.hoverDate = hoverDate;
  }

  protected void setSelectDate(SingleDate from, SingleDate to) {
    if (!checkSelection(from) || !checkSelection(to)) {
      return;
    }
    date = from;
    toDate = to;
    datePicker.runEventDateChanged();
  }

  protected void selectDate(SingleDate date) {
    if (!checkSelection(date)) {
      return;
    }
    if (dateSelectionMode == DatePicker.DateSelectionMode.SINGLE_DATE_SELECTED) {
      setDate(date);
      if (datePicker.isCloseAfterSelected()) {
        datePicker.closePopup();
      }
    } else {
      if (getDate() == null || toDate != null) {
        this.date = date;
        hoverDate = date;
        if (toDate != null) {
          toDate = null;
        }
      } else {
        toDate = date;
        datePicker.runEventDateChanged();
        if (datePicker.isCloseAfterSelected()) {
          datePicker.closePopup();
        }
      }
    }
  }

  public void setDateSelectionAble(DateSelectionAble dateSelectionAble) {
    this.dateSelectionAble = dateSelectionAble;
  }

  public DateSelectionAble getDateSelectionAble() {
    return dateSelectionAble;
  }

  private boolean checkSelection(SingleDate date) {
    if (dateSelectionAble != null) {
      return date == null || dateSelectionAble.isDateSelectedAble(date.toLocalDate());
    }
    return true;
  }
}
