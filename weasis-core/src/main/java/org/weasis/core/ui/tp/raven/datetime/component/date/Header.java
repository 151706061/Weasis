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
import java.text.DateFormatSymbols;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;

/* Header is a class that provides a header for the DatePicker.
 *
 * @author Raven Laing
 * @see <a href="https://github.com/DJ-Raven/swing-datetime-picker">swing-datetime-picker</a>
 */
public class Header extends JPanel {

  private final EventHeaderChanged headerChanged;

  public void setDate(int month, int year) {
    buttonMonth.setText(DateFormatSymbols.getInstance().getMonths()[month]);
    buttonYear.setText(year + "");
  }

  public Header(EventHeaderChanged headerChanged) {
    this.headerChanged = headerChanged;
    init();
  }

  private void init() {
    putClientProperty(FlatClientProperties.STYLE, "background:null");
    setLayout(new MigLayout("fill,insets 3", "[]push[][]push[]", "fill"));

    JButton cmdBack = createButton();
    JButton cmdNext = createButton();
    cmdBack.setIcon(ResourceUtil.getIcon(ActionIcon.PREVIOUS));
    cmdNext.setIcon(ResourceUtil.getIcon(ActionIcon.NEXT));
    buttonMonth = createButton();
    buttonYear = createButton();

    cmdBack.addActionListener(e -> headerChanged.back());
    cmdNext.addActionListener(e -> headerChanged.forward());
    buttonMonth.addActionListener(e -> headerChanged.monthSelected());
    buttonYear.addActionListener(e -> headerChanged.yearSelected());

    add(cmdBack);
    add(buttonMonth);
    add(buttonYear);
    add(cmdNext);
    setDate(10, 2023);
  }

  private JButton createButton() {
    JButton button = new JButton();
    button.putClientProperty(
        FlatClientProperties.STYLE,
        "background:null;"
            + "arc:10;"
            + "borderWidth:0;"
            + "focusWidth:0;"
            + "innerFocusWidth:0;"
            + "margin:0,5,0,5");
    return button;
  }

  private JButton buttonMonth;
  private JButton buttonYear;

  public interface EventHeaderChanged {
    void back();

    void forward();

    void monthSelected();

    void yearSelected();
  }
}
