/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.meta.panel;

import com.github.lgooddatepicker.components.DatePickerSettings;
import com.github.lgooddatepicker.tableeditors.DateTableEditor;
import com.github.lgooddatepicker.tableeditors.TimeTableEditor;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractCellEditor;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireMediaInfo;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.core.bean.SeriesGroup.Type;
import org.weasis.acquire.explorer.gui.central.meta.model.AcquireMetadataTableModel;
import org.weasis.acquire.explorer.gui.central.meta.panel.imp.AcquireSeriesMetaPanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.util.CalendarUtil;
import org.weasis.core.ui.util.LimitedTextField;
import org.weasis.core.ui.util.TableColumnAdjuster;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Sex;
import org.weasis.dicom.codec.display.Modality;

public abstract class AcquireMetadataPanel extends JPanel implements TableModelListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(AcquireMetadataPanel.class);

  protected final String title;
  protected final JTable table;
  protected AcquireMediaInfo mediaInfo;
  protected TitledBorder titleBorder;
  protected static final Font SMALL_FONT = FontItem.SMALL.getFont();

  protected AcquireMetadataPanel(String title) {
    this.title = title;
    this.titleBorder = GuiUtils.getTitledBorder(getDisplayText());
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(titleBorder);

    this.table = new JTable();
    // Force committing value when losing the focus
    table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    table.setFont(SMALL_FONT);
    table.getTableHeader().setReorderingAllowed(false);
    table.setShowHorizontalLines(true);
    table.setShowVerticalLines(true);
    table.setIntercellSpacing(GuiUtils.getDimension(2, 2));
    updateTable();
    setMetaVisible(false);
  }

  public abstract AcquireMetadataTableModel newTableModel();

  public String getDisplayText() {
    return title;
  }

  public void stopEditing() {
    TableCellEditor editor = table.getCellEditor();
    if (editor != null) {
      editor.stopCellEditing();
    }
  }

  public void setMediaInfo(AcquireMediaInfo mediaInfo) {
    this.mediaInfo = mediaInfo;
    this.titleBorder.setTitle(getDisplayText());
    setMetaVisible(mediaInfo != null);
    update();
  }

  public void setMetaVisible(boolean visible) {
    setVisible(visible);
  }

  public void update() {
    updateLabel();
    updateTable();
  }

  public void updateLabel() {
    this.titleBorder.setTitle(getDisplayText());
  }

  public void updateTable() {
    if (table.isEditing()) table.getCellEditor().stopCellEditing();
    removeAll();
    AcquireMetadataTableModel model = newTableModel();
    model.addTableModelListener(this);
    table.setModel(model);
    table.getColumnModel().getColumn(1).setCellRenderer(new TagRenderer());
    SeriesGroup group = null;
    if (this instanceof AcquireSeriesMetaPanel panel) {
      group = panel.getSeries();
    }
    table.getColumnModel().getColumn(1).setCellEditor(new AcquireImageCellEditor(group, mediaInfo));
    TableColumnAdjuster.pack(table);
    add(table.getTableHeader());
    add(table);
  }

  @Override
  public void tableChanged(TableModelEvent e) {
    // DO NOTHING
  }

  public static class TagRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(
        JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component val =
          super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

      AcquireMetadataTableModel model = (AcquireMetadataTableModel) table.getModel();
      if (model.isCellEditable(row, column)) {
        Color c =
            model.isValueRequired(row) && isEmptyValue(value)
                ? IconColor.ACTIONS_RED.getColor()
                : getForeground();
        setBorder(BorderFactory.createDashedBorder(c, 2, 2));
      }

      Object tag = model.getValueAt(row, 0);
      if (tag instanceof TagW tagW) {
        setValue(tagW.getFormattedTagValue(value, null));
      }
      return val;
    }

    private boolean isEmptyValue(Object value) {
      if (value == null) {
        return true;
      }
      if (value instanceof String s) {
        return !StringUtil.hasText(s);
      }
      return false;
    }
  }

  public static class AcquireImageCellEditor extends AbstractCellEditor implements TableCellEditor {
    private static final String[] studyDescValues =
        getValues("weasis.acquire.meta.study.description", null);
    private static final JComboBox<TagD.Sex> sexCombo = new JComboBox<>(TagD.Sex.values());
    private static final JComboBox<Modality> modalityCombo =
        new JComboBox<>(Modality.getAllModalitiesExceptDefault());
    private static final JComboBox<String> studyDescCombo = new JComboBox<>(studyDescValues);
    private static final JComboBox<String> seriesDescCombo =
        new JComboBox<>(getValues("weasis.acquire.meta.series.description", null));

    static {
      initCombo(sexCombo);
      initCombo(modalityCombo);
      initCombo(studyDescCombo);
      initCombo(seriesDescCombo);
    }

    private final AcquireMediaInfo mediaInfo;
    private Optional<TableCellEditor> editor;

    public AcquireImageCellEditor(SeriesGroup group, AcquireMediaInfo mediaInfo) {
      this.mediaInfo = mediaInfo;
      if (group != null) {
        Type type = group.getType();
        if (type == Type.IMAGE || type == Type.IMAGE_NAME || type == Type.IMAGE_DATE) {
          studyDescCombo.setModel(new DefaultComboBoxModel<>(studyDescValues));
        } else {
          studyDescCombo.removeAllItems();
        }
      }
    }

    @Override
    public Object getCellEditorValue() {
      return editor.map(e -> convertValue(e.getCellEditorValue())).orElse(null);
    }

    private Object convertValue(Object val) {
      if (val instanceof TagD.Sex sex) {
        return sex.getValue();
      } else if (val instanceof Modality modality) {
        return modality.name();
      }
      return val;
    }

    @Override
    public Component getTableCellEditorComponent(
        JTable table, Object value, boolean isSelected, int row, int column) {
      TableCellEditor cellEditor;
      Object tag = table.getModel().getValueAt(row, 0);
      int tagID = 0;
      boolean date = false;
      boolean time = false;
      int limitedChars = 64;
      if (tag instanceof TagW tagW) {
        tagID = tagW.getId();
        TagType type = tagW.getType();
        date = TagType.DICOM_DATE == type || TagType.DATE == type;
        time = TagType.DICOM_TIME == type || TagType.TIME == type;
        if (tag instanceof TagD tagD) {
          limitedChars = tagD.getMaximumChars();
        }
      }
      if (tagID == TagW.AnatomicRegion.getId()) {
        cellEditor = new AnatomicRegionCellEditor(mediaInfo);
      } else if (tagID == Tag.PatientSex) {
        cellEditor = getTableCellEditor(Sex.getSex((String) value), sexCombo, limitedChars);
      } else if (tagID == Tag.Modality) {
        Modality modality = Modality.getModality((String) value);
        cellEditor = getTableCellEditor(modality, modalityCombo, limitedChars);
      } else if (tagID == Tag.StudyDescription) {
        cellEditor = getTableCellEditor(value, studyDescCombo, limitedChars);
      } else if (tagID == Tag.SeriesDescription) {
        cellEditor = getTableCellEditor(value, seriesDescCombo, limitedChars);
      } else if (date) {
        DateTableEditor datePicker = buildDatePicker();
        JTextField picker = datePicker.getDatePicker().getComponentDateTextField();
        Insets margin = picker.getMargin();
        int height = table.getRowHeight(row) - margin.top - margin.bottom;
        GuiUtils.setPreferredHeight(picker, height);
        GuiUtils.setPreferredHeight(
            datePicker.getDatePicker().getComponentToggleCalendarButton(), height);
        cellEditor = datePicker;
      } else if (time) {
        TimeTableEditor tableEditor = new TimeTableEditor(false, true, true);
        tableEditor.getTimePickerSettings().fontInvalidTime = SMALL_FONT;
        tableEditor.getTimePickerSettings().fontValidTime = SMALL_FONT;
        tableEditor.getTimePickerSettings().fontVetoedTime = SMALL_FONT;
        JButton button = tableEditor.getTimePicker().getComponentToggleTimeMenuButton();
        Insets margin = button.getMargin();
        int height = table.getRowHeight(row) - margin.top - margin.bottom;
        GuiUtils.setPreferredHeight(button, height, height);
        GuiUtils.setPreferredHeight(tableEditor.getTimePicker(), height, height);
        GuiUtils.setPreferredHeight(
            tableEditor.getTimePicker().getComponentTimeTextField(), height, height);
        cellEditor = tableEditor;
      } else {
        cellEditor = new DefaultCellEditor(new LimitedTextField(limitedChars));
      }
      editor = Optional.of(cellEditor);
      Component c = cellEditor.getTableCellEditorComponent(table, value, isSelected, row, column);
      c.setFont(SMALL_FONT);
      return c;
    }

    private static TableCellEditor getTableCellEditor(
        Object value, JComboBox<?> studyDescCombo, int limitedChars) {
      if (value != null) {
        studyDescCombo.setSelectedItem(value);
      }
      return getCellEditor(studyDescCombo, limitedChars);
    }

    private static DefaultCellEditor getCellEditor(JComboBox<?> combo, int limitedChars) {
      if (combo.getItemCount() == 0) {
        return new DefaultCellEditor(new LimitedTextField(limitedChars));
      } else {
        return new DefaultCellEditor(combo);
      }
    }

    private static void initCombo(JComboBox<?> combo) {
      combo.setFont(AcquireMetadataPanel.SMALL_FONT);
      combo.setMaximumRowCount(15);
      GuiUtils.setPreferredWidth(combo, 80);
    }

    private DateTableEditor buildDatePicker() {
      DateTableEditor d = new DateTableEditor(false, true, true);
      DatePickerSettings settings = d.getDatePickerSettings();
      settings.setFontInvalidDate(SMALL_FONT);
      settings.setFontValidDate(SMALL_FONT);
      settings.setFontVetoedDate(SMALL_FONT);

      CalendarUtil.adaptCalendarColors(settings);

      settings.setFormatForDatesCommonEra(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
      settings.setFormatForDatesBeforeCommonEra(
          DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));

      settings.setFormatForDatesCommonEra(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
      settings.setFormatForDatesBeforeCommonEra(
          DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
      return d;
    }

    public static String[] getValues(String property, String defaultValues) {
      String values =
          GuiUtils.getUICore().getSystemPreferences().getProperty(property, defaultValues);
      if (values == null) {
        return new String[0];
      }
      String[] val = values.split(",");
      List<String> list = new ArrayList<>(val.length);
      for (String s : val) {
        if (StringUtil.hasText(s)) {
          list.add(s.trim());
        }
      }
      return list.toArray(new String[0]);
    }
  }
}
