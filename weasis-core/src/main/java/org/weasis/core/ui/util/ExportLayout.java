/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import static org.weasis.core.ui.editor.image.ImageViewerPlugin.VIEWS_1x1;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.plaf.PanelUI;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.layout.LayoutCellManager;
import org.weasis.core.api.gui.layout.MigCell;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ExportImage;
import org.weasis.core.ui.editor.image.ViewCanvas;

public class ExportLayout<E extends ImageElement> extends JPanel {

  protected final JPanel grid = new JPanel();
  protected final LayoutCellManager<E> cellManager = new LayoutCellManager<>(VIEWS_1x1.copy());

  public ExportLayout(MigLayoutModel layoutModel) {
    initGrid();
    adaptLayoutModel(layoutModel);
  }

  public ExportLayout(ViewCanvas<E> viewCanvas) {
    initGrid();
    adaptLayoutModelFromCanvas(viewCanvas);
  }

  private void initGrid() {
    // For having a black background with any Look and Feel
    grid.setUI(new PanelUI() {});
    setGridBackground(Color.BLACK);
    add(grid, BorderLayout.CENTER);
  }

  public void setGridBackground(Color bg) {
    grid.setBackground(bg);
  }

  /** Get the layout of this view panel. */
  public MigLayoutModel getLayoutModel() {
    return cellManager.getLayoutModel();
  }

  private void adaptLayoutModelFromCanvas(ViewCanvas<E> viewCanvas) {
    // Create a simple 1x1 layout for a single canvas export
    MigLayoutModel layoutModel =
        new MigLayoutModel(
            "exp_tmp",
            "",
            "wrap 1, ins 0, gap 0",
            "[grow,fill]",
            "[grow,fill]",
            List.of(new MigCell(0, viewCanvas.getClass().getName(), "grow", 0, 0, 1, 1)));

    cellManager.setLayoutModel(layoutModel);

    ExportImage<E> export = new ExportImage<>(viewCanvas);
    export.getInfoLayer().setBorder(3);
    cellManager.addComponent(0, export);

    // Apply layout using MigLayout directly
    grid.setLayout(
        new MigLayout(
            layoutModel.getLayoutConstraints(),
            layoutModel.getColumnConstraints(),
            layoutModel.getRowConstraints()));
    grid.add(export, "grow");
    grid.revalidate();
  }

  private void adaptLayoutModel(MigLayoutModel layoutModel) {
    // TODO should use cellManager to add components
    cellManager.setLayoutModel(layoutModel);

    // Apply MigLayout to grid
    grid.setLayout(
        new MigLayout(
            layoutModel.getLayoutConstraints(),
            layoutModel.getColumnConstraints(),
            layoutModel.getRowConstraints()));

    cellManager.getLayoutModel().applyConstraintsToLayout(grid);
  }

  /**
   * Replaces a component in the grid at the specified position.
   *
   * @param position the cell position
   * @param component the new component
   */
  public void replaceComponent(int position, Component component) {
    Component oldComponent = cellManager.getComponent(position);
    if (oldComponent != null) {
      grid.remove(oldComponent);
    }

    LayoutCellManager.CellEntry<E> entry = cellManager.replaceComponent(position, component);
    if (entry != null) {
      grid.add(component, entry.getCell().getFullConstraints());
      grid.revalidate();
      grid.repaint();
    }
  }

  /**
   * Adds an ExportImage for a ViewCanvas at the specified cell position.
   *
   * @param viewCanvas the canvas to export
   * @param cellPosition the position in the layout
   */
  public void addExportImage(ViewCanvas<?> viewCanvas, int cellPosition) {
    if (viewCanvas == null) {
      return;
    }

    ExportImage<?> export = new ExportImage<>(viewCanvas);
    export.getInfoLayer().setBorder(3);
    replaceComponent(cellPosition, export);
  }

  public void dispose() {
    for (var c : cellManager.getAllViewCanvases()) {
      if (c instanceof ExportImage<?> exportImage) {
        exportImage.disposeView();
      }
    }
    cellManager.clear();
  }

  public Component findComponentForCell(MigCell cell) {
    return cellManager.getComponent(cell.position());
  }
}
