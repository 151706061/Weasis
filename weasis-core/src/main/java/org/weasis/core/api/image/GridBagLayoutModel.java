/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.geom.Rectangle2D;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GUIEntry;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.Copyable;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.util.StringUtil;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Model for grid-based layout management in plugin containers.
 *
 * <p>Manages layout constraints for component positioning using GridBagLayout and provides visual
 * representation through dynamically generated icons. Supports initialization from code, XML
 * streams, or copy construction.
 */
public final class GridBagLayoutModel implements GUIEntry, Copyable<GridBagLayoutModel> {

  private static final Logger LOGGER = LoggerFactory.getLogger(GridBagLayoutModel.class);
  private static final int ICON_SIZE = 22;
  private static final String ELEMENT_TAG = "element";
  private static final String LAYOUT_MODEL_TAG = "layoutModel";
  private static final String FRACTION_SEPARATOR = "/";

  private String title;
  private Icon icon;
  private final String id;

  private final Map<LayoutConstraints, Component> constraints;

  /**
   * Creates a model with uniform grid layout.
   *
   * @param id unique identifier for this layout
   * @param title display name
   * @param rows number of rows
   * @param cols number of columns
   * @param defaultClass default component class name
   */
  public GridBagLayoutModel(String id, String title, int rows, int cols, String defaultClass) {
    this(createGridConstraints(rows, cols, defaultClass), id, title);
  }

  /**
   * Creates a model with predefined constraints.
   *
   * @param constraints map of layout constraints to components
   * @param id unique identifier
   * @param title display name
   */
  public GridBagLayoutModel(
      Map<LayoutConstraints, Component> constraints, String id, String title) {
    this.title = Objects.requireNonNull(title, "title cannot be null");
    this.id = Objects.requireNonNull(id, "id cannot be null");
    this.constraints = Objects.requireNonNull(constraints, "constraints cannot be null");
    this.icon = buildIcon();
  }

  /**
   * Creates a model from XML stream.
   *
   * @param stream XML input stream containing layout definition
   * @param id unique identifier
   * @param title display name
   */
  public GridBagLayoutModel(InputStream stream, String id, String title) {
    this.title = title;
    this.id = Objects.requireNonNull(id, "id cannot be null");
    this.constraints = new LinkedHashMap<>();
    parseXmlLayout(stream);
    this.icon = Optional.ofNullable(icon).orElseGet(this::buildIcon);
  }

  /** Copy constructor. */
  public GridBagLayoutModel(GridBagLayoutModel layoutModel) {
    this.title = layoutModel.title;
    this.id = layoutModel.id;
    this.icon = layoutModel.icon;
    this.constraints = copyConstraints(layoutModel.constraints);
  }

  private static Map<LayoutConstraints, Component> createGridConstraints(
      int rows, int cols, String defaultClass) {
    var constraints = new LinkedHashMap<LayoutConstraints, Component>(cols * rows);
    double weightX = 1.0 / cols;
    double weightY = 1.0 / rows;

    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        var constraint =
            new LayoutConstraints(
                defaultClass,
                y * cols + x + 1,
                x,
                y,
                1,
                1,
                weightX,
                weightY,
                GridBagConstraints.CENTER,
                GridBagConstraints.BOTH);
        constraints.put(constraint, null);
      }
    }
    return constraints;
  }

  private static Map<LayoutConstraints, Component> copyConstraints(
      Map<LayoutConstraints, Component> original) {
    var copy = new LinkedHashMap<LayoutConstraints, Component>(original.size());
    original.keySet().forEach(constraint -> copy.put(constraint.copy(), null));
    return copy;
  }

  private void parseXmlLayout(InputStream stream) {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      configureSecureXmlParser(factory);
      SAXParser parser = factory.newSAXParser();
      parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      parser.parse(stream, new LayoutXmlHandler());
    } catch (Exception e) {
      LOGGER.error("Failed to parse layout XML", e);
    }
  }

  private void configureSecureXmlParser(SAXParserFactory factory) throws Exception {
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
  }

  private Icon buildIcon() {
    return new LayoutIcon();
  }

  public String getId() {
    return id;
  }

  @Override
  public String toString() {
    return title;
  }

  public Map<LayoutConstraints, Component> getConstraints() {
    return constraints;
  }

  /** Returns the grid dimensions based on maximum coordinates. */
  public Dimension getGridSize() {
    return constraints.keySet().stream()
        .reduce(
            new Dimension(0, 0),
            (dim, constraint) ->
                new Dimension(
                    Math.max(dim.width, constraint.gridx + 1),
                    Math.max(dim.height, constraint.gridy + 1)),
            (dim1, dim2) ->
                new Dimension(
                    Math.max(dim1.width, dim2.width), Math.max(dim1.height, dim2.height)));
  }

  @Override
  public String getDescription() {
    return null;
  }

  @Override
  public Icon getIcon() {
    return icon;
  }

  public void setIcon(Icon icon) {
    this.icon = icon;
  }

  @Override
  public String getUIName() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  @Override
  public GridBagLayoutModel copy() {
    return new GridBagLayoutModel(this);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof GridBagLayoutModel that
        && Objects.equals(id, that.id)
        && Objects.equals(title, that.title)
        && Objects.equals(constraints, that.constraints);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, title, constraints);
  }

  /** Icon renderer for layout visualization. */
  private final class LayoutIcon implements Icon {

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      Graphics2D g2d = (Graphics2D) g;
      Color originalColor = g2d.getColor();
      Object[] renderingHints = GuiUtils.setRenderingHints(g2d, true, true, false);

      try {
        ColorScheme colorScheme = determineColorScheme(c);
        drawIconBackground(g2d, x, y, colorScheme.background);
        drawGridCells(g2d, x, y, colorScheme.foreground);
      } finally {
        g2d.setColor(originalColor);
        GuiUtils.resetRenderingHints(g2d, renderingHints);
      }
    }

    private ColorScheme determineColorScheme(Component c) {
      Color background = IconColor.ACTIONS_GREY.color;
      Color foreground = FlatUIUtils.getUIColor("MenuItem.background", Color.WHITE);

      if (c instanceof RadioMenuItem menuItem) {
        if (menuItem.isArmed()) {
          background = FlatUIUtils.getUIColor("MenuItem.selectionForeground", Color.DARK_GRAY);
          foreground = FlatUIUtils.getUIColor("MenuItem.selectionBackground", Color.LIGHT_GRAY);
        } else if (menuItem.isSelected()) {
          foreground = FlatUIUtils.getUIColor("MenuItem.checkBackground", Color.BLUE);
        }
      }

      return new ColorScheme(background, foreground);
    }

    private void drawIconBackground(Graphics2D g2d, int x, int y, Color background) {
      g2d.setColor(background);
      g2d.fillRect(x, y, getIconWidth(), getIconHeight());
    }

    private void drawGridCells(Graphics2D g2d, int x, int y, Color foreground) {
      Dimension gridSize = getGridSize();
      double stepX = (double) getIconWidth() / gridSize.width;
      double stepY = (double) getIconHeight() / gridSize.height;

      for (LayoutConstraints constraint : constraints.keySet()) {
        Rectangle2D cellRect = createCellRectangle(constraint, x, y, stepX, stepY);
        drawCell(g2d, cellRect, constraint.color(), foreground);
      }
    }

    private Rectangle2D createCellRectangle(
        LayoutConstraints constraint, int x, int y, double stepX, double stepY) {
      return new Rectangle2D.Double(
          x + constraint.gridx * stepX,
          y + constraint.gridy * stepY,
          constraint.gridwidth * stepX,
          constraint.gridheight * stepY);
    }

    private void drawCell(Graphics2D g2d, Rectangle2D rect, Color cellColor, Color borderColor) {
      if (cellColor != null) {
        g2d.setColor(cellColor);
        g2d.fill(rect);
      }

      g2d.setColor(borderColor);
      g2d.draw(rect);
    }

    @Override
    public int getIconWidth() {
      return GuiUtils.getScaleLength(ICON_SIZE);
    }

    @Override
    public int getIconHeight() {
      return GuiUtils.getScaleLength(ICON_SIZE);
    }
  }

  /** Color scheme for icon rendering. */
  private record ColorScheme(Color background, Color foreground) {}

  /** SAX handler for parsing layout XML files. */
  private final class LayoutXmlHandler extends DefaultHandler {
    private int x, y, width, height, position, expand;
    private double weightx, weighty;
    private String type;

    private int increment = 0;

    private Color color;

    private final StringBuilder textBuffer = new StringBuilder(80);

    @Override
    public void characters(char[] ch, int start, int length) {
      textBuffer.append(ch, start, length);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
      switch (qName) {
        case ELEMENT_TAG -> parseElementAttributes(attributes);
        case LAYOUT_MODEL_TAG -> parseLayoutModelAttributes(attributes);
      }
    }

    private void parseElementAttributes(Attributes attributes) {
      type = attributes.getValue("type");
      x = parseIntAttribute(attributes, "x");
      y = parseIntAttribute(attributes, "y");
      width = parseIntAttribute(attributes, "width");
      height = parseIntAttribute(attributes, "height");
      weightx = parseDoubleAttribute(attributes, "weightx");
      weighty = parseDoubleAttribute(attributes, "weighty");
      position = parseIntAttribute(attributes, "position");
      expand = parseIntAttribute(attributes, "expand");

      String colorType = attributes.getValue("typeColor");
      color = StringUtil.hasText(colorType) ? WProperties.hexadecimal2Color(colorType) : null;
    }

    private void parseLayoutModelAttributes(Attributes attributes) {
      if (title == null) {
        title = attributes.getValue("name");
      }
      String iconPath = attributes.getValue("icon");
      if (StringUtil.hasText(iconPath)) {
        icon = ResourceUtil.getIcon(iconPath).derive(ICON_SIZE, ICON_SIZE);
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
      if (ELEMENT_TAG.equals(qName)) {
        addConstraint();
        resetElementState();
      }
    }

    private void addConstraint() {
      increment++;
      var constraint =
          new LayoutConstraints(
              type, increment, x, y, width, height, weightx, weighty, position, expand);
      constraint.setColor(color);
      constraints.put(constraint, null);
    }

    private void resetElementState() {
      textBuffer.setLength(0);
      color = null;
    }

    private int parseIntAttribute(Attributes attributes, String name) {
      String value = attributes.getValue(name);
      return StringUtil.hasText(value) ? Integer.parseInt(value) : 0;
    }

    private double parseDoubleAttribute(Attributes attributes, String name) {
      String value = attributes.getValue(name);
      return StringUtil.hasText(value) ? parseDoubleValue(value) : 0.0;
    }

    private double parseDoubleValue(String value) {
      if (!StringUtil.hasText(value)) {
        return 0.0;
      }
      int fractionIndex = value.indexOf(FRACTION_SEPARATOR);
      if (fractionIndex != -1) {
        return parseFraction(value, fractionIndex);
      }
      return Double.parseDouble(value);
    }

    private double parseFraction(String value, int separatorIndex) {
      int numerator = Integer.parseInt(value.substring(0, separatorIndex));
      int denominator = Integer.parseInt(value.substring(separatorIndex + 1));
      return (double) numerator / denominator;
    }
  }
}
