/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.swing.JDialog;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.FilterOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.ui.dialog.MeasureDialog;
import org.weasis.core.ui.editor.image.CalibrationView;
import org.weasis.core.ui.editor.image.ContextMenuHandler;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.AbstractGraphicModel;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.area.PolygonGraphic;
import org.weasis.core.ui.model.graphic.imp.area.RectangleGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.LineWithGapGraphic;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.layer.GraphicLayer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.ui.model.utils.bean.PanPoint.State;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.MouseEventDouble;
import org.weasis.core.ui.util.TitleMenuItem;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.MathUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.LazyContourLoader;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.OverlayOp;
import org.weasis.dicom.codec.display.ShutterOp;
import org.weasis.dicom.codec.display.WindowAndPresetsOp;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;
import org.weasis.dicom.codec.geometry.IntersectSlice;
import org.weasis.dicom.codec.geometry.IntersectVolume;
import org.weasis.dicom.codec.geometry.LocalizerPoster;
import org.weasis.dicom.codec.geometry.PatientOrientation.Biped;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSeriesHandler;
import org.weasis.dicom.explorer.pr.PrGraphicUtil;
import org.weasis.dicom.viewer2d.KOComponentFactory.KOViewButton;
import org.weasis.dicom.viewer2d.KOComponentFactory.KOViewButton.eState;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.WlPresentation;

public class View2d extends DefaultView2d<DicomImageElement> {

  private static final Logger LOGGER = LoggerFactory.getLogger(View2d.class);

  public static final String P_CROSSHAIR_CENTER_GAP = "mpr.crosshair.center.gap";
  public static final String P_CROSSHAIR_MODE = "mpr.crosshair.mode";
  private final Dimension oldSize;
  private final ContextMenuHandler contextMenuHandler;

  protected final KOViewButton koStarButton;

  public View2d(ImageViewerEventManager<DicomImageElement> eventManager) {
    super(eventManager);

    SimpleOpManager manager = imageLayer.getDisplayOpManager();
    manager.addImageOperationAction(new WindowAndPresetsOp());
    manager.addImageOperationAction(new FilterOp());
    manager.addImageOperationAction(new PseudoColorOp());
    manager.addImageOperationAction(new ShutterOp());
    manager.addImageOperationAction(new OverlayOp());
    // Zoom and Rotation must be the last operations for the lens
    manager.addImageOperationAction(new AffineTransformOp());

    this.contextMenuHandler = new ContextMenuHandler(this);
    this.infoLayer = new InfoLayer(this);
    this.oldSize = new Dimension(0, 0);

    // TODO should be a lazy instantiation
    getViewButtons().add(KOComponentFactory.buildKoSelectionButton());
    this.koStarButton = KOComponentFactory.buildKoStarButton(this);
    koStarButton.setPosition(GridBagConstraints.NORTHEAST);
    getViewButtons().add(koStarButton);
  }

  @Override
  public void registerDefaultListeners() {
    super.registerDefaultListeners();
    setTransferHandler(new DicomSeriesHandler(this));
    addComponentListener(
        new ComponentAdapter() {

          @Override
          public void componentResized(ComponentEvent e) {
            View2d.this.componentResized();
          }
        });
  }

  private void componentResized() {
    Double currentZoom = (Double) actionsInView.get(ActionW.ZOOM.cmd());
    /*
     * Negative value means a default value according to the zoom type (pixel size, best fit...). Set again to
     * default value to compute again the position. For instance, the image cannot be center aligned until the view
     * has been repaint once (because the size is null).
     */
    if (currentZoom <= 0.0) {
      zoom(0.0);
    } else {
      zoom(currentZoom);
    }
    if (panner != null) {
      panner.updateImageSize();
    }
    if (lens != null) {
      int w = getWidth();
      int h = getHeight();
      if (w != 0 && h != 0) {
        Rectangle bound = lens.getBounds();
        if (oldSize.width != 0 && oldSize.height != 0) {
          int centerX = bound.width / 2;
          int centerY = bound.height / 2;
          bound.x = (bound.x + centerX) * w / oldSize.width - centerX;
          bound.y = (bound.y + centerY) * h / oldSize.height - centerY;
          lens.setLocation(bound.x, bound.y);
        }
        oldSize.width = w;
        oldSize.height = h;
      }
      lens.updateZoom();
    }
  }

  @Override
  protected void initActionWState() {
    super.initActionWState();
    actionsInView.put(ActionW.SORT_STACK.cmd(), SortSeriesStack.instanceNumber);

    // Preprocessing
    actionsInView.put(ActionW.CROP.cmd(), null);

    OpManager disOp = getDisplayOpManager();
    disOp.setParamValue(ShutterOp.OP_NAME, ShutterOp.P_SHOW, true);
    disOp.setParamValue(OverlayOp.OP_NAME, OverlayOp.P_SHOW, true);
    disOp.setParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), true);
    disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), true);
    disOp.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), null);

    initKOActionWState();
  }

  protected void initKOActionWState() {
    actionsInView.put(ActionW.KO_FILTER.cmd(), false);
    actionsInView.put(ActionW.KO_TOGGLE_STATE.cmd(), false);
    actionsInView.put(ActionW.KO_SELECTION.cmd(), ActionState.NoneLabel.NONE);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    super.propertyChange(evt);
    if (series == null) {
      return;
    }

    PlanarImage dispImage = imageLayer.getDisplayImage();
    OpManager disOp = imageLayer.getDisplayOpManager();
    final String name = evt.getPropertyName();
    if (name.equals(ActionW.SYNCH.cmd())) {
      SynchEvent synch = (SynchEvent) evt.getNewValue();
      SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
      boolean tile = synchData != null && SynchData.Mode.TILE.equals(synchData.getMode());
      if (synchData != null && Mode.NONE.equals(synchData.getMode())) {
        return;
      }
      for (Entry<String, Object> entry : synch.getEvents().entrySet()) {
        final String command = entry.getKey();
        final Object val = entry.getValue();
        if (synchData != null && !synchData.isActionEnable(command)) {
          continue;
        }

        if (command.equals(ActionW.PRESET.cmd())) {
          actionsInView.put(ActionW.PRESET.cmd(), val);

          if (val instanceof PresetWindowLevel preset) {
            DicomImageElement img = getImage();
            ImageOpNode node = disOp.getNode(WindowOp.OP_NAME);

            if (node != null) {
              node.setParam(ActionW.WINDOW.cmd(), preset.getWindow());
              node.setParam(ActionW.LEVEL.cmd(), preset.getLevel());
              node.setParam(ActionW.LUT_SHAPE.cmd(), preset.getLutShape());
              // When series synchronization, do not synch preset from other series
              if (img == null || !img.containsPreset(preset)) {
                List<PresetWindowLevel> presets =
                    (List<PresetWindowLevel>) actionsInView.get(PRManager.PR_PRESETS);
                if (presets == null || !presets.contains(preset)) {
                  preset = null;
                }
              }
              node.setParam(ActionW.PRESET.cmd(), preset);
            }
            imageLayer.updateDisplayOperations();
          }
        } else if (command.equals(ActionW.DEFAULT_PRESET.cmd())) {
          disOp.setParamValue(WindowOp.OP_NAME, ActionW.DEFAULT_PRESET.cmd(), val);
        } else if (command.equals(ActionW.LUT_SHAPE.cmd())) {
          ImageOpNode node = disOp.getNode(WindowOp.OP_NAME);
          if (node != null) {
            node.setParam(ActionW.LUT_SHAPE.cmd(), val);
          }
          imageLayer.updateDisplayOperations();
        } else if (command.equals(ActionW.SORT_STACK.cmd())) {
          actionsInView.put(ActionW.SORT_STACK.cmd(), val);
          sortStack(getCurrentSortComparator());
        } else if (command.equals(ActionW.INVERSE_STACK.cmd())) {
          actionsInView.put(ActionW.INVERSE_STACK.cmd(), val);
          sortStack(getCurrentSortComparator());
        } else if (command.equals(ActionW.KO_SELECTION.cmd())) {
          int frameIndex =
              tile
                  ? LangUtil.getNULLtoFalse(
                          (Boolean) synch.getView().getActionValue(ActionW.KO_FILTER.cmd()))
                      ? 0
                      : synch.getView().getFrameIndex() - synch.getView().getTileOffset()
                  : -1;
          KOManager.updateKOFilter(
              this,
              val,
              (Boolean) (tile ? synch.getView().getActionValue(ActionW.KO_FILTER.cmd()) : null),
              frameIndex);
        } else if (command.equals(ActionW.KO_FILTER.cmd())) {
          int frameIndex =
              tile
                  ? LangUtil.getNULLtoFalse((Boolean) val)
                      ? 0
                      : synch.getView().getFrameIndex() - synch.getView().getTileOffset()
                  : -1;
          KOManager.updateKOFilter(
              this,
              tile ? synch.getView().getActionValue(ActionW.KO_SELECTION.cmd()) : null,
              (Boolean) val,
              frameIndex);
        } else if (command.equals(ActionW.CROSSHAIR.cmd())
            && series != null
            && val instanceof PanPoint p) {
          GeometryOfSlice sliceGeometry = this.getImage().getSliceGeometry();
          String fruid = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
          ImageViewerPlugin<DicomImageElement> container =
              eventManager.getSelectedView2dContainer();
          if (container != null) {
            crosshairAction(sliceGeometry, container.getView2ds(), this, fruid, p);
          }
        }
      }
    } else if (name.equals(ActionW.IMAGE_SHUTTER.cmd())) {
      if (disOp.setParamValue(ShutterOp.OP_NAME, ShutterOp.P_SHOW, evt.getNewValue())) {
        imageLayer.updateDisplayOperations();
      }
    } else if (name.equals(ActionW.IMAGE_OVERLAY.cmd())
        && disOp.setParamValue(OverlayOp.OP_NAME, OverlayOp.P_SHOW, evt.getNewValue())) {
      imageLayer.updateDisplayOperations();
    }

    if (lens != null && dispImage != imageLayer.getDisplayImage()) {
      /*
       * Transmit to the lens the command in case the source image has been freeze (for updating rotation and
       * flip => will keep consistent display)
       */
      lens.setCommandFromParentView(name, evt.getNewValue());
      lens.updateZoom();
    }
  }

  public static void crosshairAction(
      GeometryOfSlice sliceGeometry,
      List<ViewCanvas<DicomImageElement>> view2ds,
      ViewCanvas<DicomImageElement> selectedView,
      String fruid,
      PanPoint p) {
    if (sliceGeometry != null && fruid != null) {
      Vector3d p3 = Double.isNaN(p.getX()) ? null : sliceGeometry.getPosition(p);
      if (view2ds != null && p3 != null) {
        Map<String, Object> actionsInView = selectedView.getActionsInView();
        for (ViewCanvas<DicomImageElement> v : view2ds) {
          MediaSeries<DicomImageElement> s = v.getSeries();
          if (s == null) {
            continue;
          }
          if (v instanceof View2d view2d
              && fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID))
              && v != selectedView) {
            DicomImageElement imgToUpdate = v.getImage();
            if (imgToUpdate != null) {
              GeometryOfSlice geometry = imgToUpdate.getSliceGeometry();
              if (geometry != null) {
                Vector3d vn = geometry.getNormal();
                // vn.absolute();
                double location = p3.x * vn.x + p3.y * vn.y + p3.z * vn.z;
                DicomImageElement img =
                    s.getNearestImage(
                        location,
                        0,
                        (Filter<DicomImageElement>)
                            actionsInView.get(ActionW.FILTERED_SERIES.cmd()),
                        v.getCurrentSortComparator());
                if (img != null) {
                  Object oldZoomType = actionsInView.get(ViewCanvas.ZOOM_TYPE_CMD);
                  view2d.setActionsInView(ViewCanvas.ZOOM_TYPE_CMD, ZoomType.CURRENT);
                  view2d.setImage(img);
                  view2d.setActionsInView(ViewCanvas.ZOOM_TYPE_CMD, oldZoomType);
                }
              }
            }
          }
        }
        for (ViewCanvas<DicomImageElement> v : view2ds) {
          MediaSeries<DicomImageElement> s = v.getSeries();
          if (s == null) {
            continue;
          }
          if (v instanceof View2d view2d
              && fruid.equals(TagD.getTagValue(s, Tag.FrameOfReferenceUID))
              && LangUtil.getNULLtoTrue((Boolean) actionsInView.get(LayerType.CROSSLINES.name()))) {
            view2d.computeCrosshair(p3, p);
            view2d.repaint();
          }
        }
      }
    }
  }

  void setPresentationState(Object val, boolean newImage) {

    Object old = actionsInView.get(ActionW.PR_STATE.cmd());
    if (!newImage && Objects.equals(old, val)) {
      return;
    }

    actionsInView.put(ActionW.PR_STATE.cmd(), val);
    PresentationStateReader pr =
        val instanceof PRSpecialElement prSpecialElement
            ? new PresentationStateReader(prSpecialElement)
            : null;
    boolean spatialTransformation = actionsInView.get(ActionW.PREPROCESSING.cmd()) != null;
    actionsInView.put(ActionW.PREPROCESSING.cmd(), null);

    DicomImageElement m = getImage();
    // Reset display parameter
    ((DefaultViewModel) getViewModel()).setEnableViewModelChangeListeners(false);
    imageLayer.setEnableDispOperations(false);
    imageLayer.fireOpEvent(new ImageOpEvent(ImageOpEvent.OpEvent.RESET_DISPLAY, series, m, null));

    boolean changePixConfig =
        LangUtil.getNULLtoFalse((Boolean) actionsInView.get(PRManager.TAG_CHANGE_PIX_CONFIG));
    if (m != null) {
      // Restore the original image pixel size
      if (changePixConfig) {
        m.initPixelConfiguration();
        eventManager
            .getAction(ActionW.SPATIAL_UNIT)
            .ifPresent(s -> s.setSelectedItem(m.getPixelSpacingUnit()));
      }
      deletePrLayers();

      // Restore presets
      actionsInView.remove(PRManager.PR_PRESETS);

      // Restore zoom
      actionsInView.remove(PRManager.TAG_PR_ZOOM);
      actionsInView.remove(PresentationStateReader.TAG_PR_ROTATION);
      actionsInView.remove(PresentationStateReader.TAG_PR_FLIP);

      // Reset crop
      updateCanvas(false);
      getImageLayer().setOffset(null);
    }
    // If no Presentation State use the current image
    if (pr == null) {
      // Keeps KO properties (series level)
      Object ko = actionsInView.get(ActionW.KO_SELECTION.cmd());
      Object filter = actionsInView.get(ActionW.FILTERED_SERIES.cmd());
      OpManager disOp = getDisplayOpManager();
      Object preset = disOp.getParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd());
      initActionWState();
      setActionsInView(ActionW.KO_SELECTION.cmd(), ko);
      setActionsInView(ActionW.FILTERED_SERIES.cmd(), filter);
      // Set the image spatial unit
      if (m != null) {
        setActionsInView(ActionW.SPATIAL_UNIT.cmd(), m.getPixelSpacingUnit());
      }
      disOp.setParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd(), preset);
      resetZoom();
      resetPan();
    } else {
      PRManager.applyPresentationState(this, pr, m);
    }

    Rectangle area = (Rectangle) actionsInView.get(ActionW.CROP.cmd());
    if (area != null && !area.equals(getViewModel().getModelArea())) {
      ((DefaultViewModel) getViewModel()).adjustMinViewScaleFromImage(area.width, area.height);
      getViewModel().setModelArea(new Rectangle(0, 0, area.width, area.height));
      getImageLayer().setOffset(new Point(area.x, area.y));
    }

    SimpleOpManager opManager = (SimpleOpManager) actionsInView.get(ActionW.PREPROCESSING.cmd());
    imageLayer.setPreprocessing(opManager);
    if (opManager != null || spatialTransformation) {
      // Reset preprocessing cache
      if (opManager == null && m != null) {
        m.resetImageAvailable();
      }
      imageLayer.getDisplayOpManager().setFirstNode(imageLayer.getSourceRenderedImage());
    }

    if (pr != null) {
      imageLayer.fireOpEvent(
          new ImageOpEvent(ImageOpEvent.OpEvent.APPLY_PR, series, m, actionsInView));
      actionsInView.put(
          ActionW.ROTATION.cmd(), actionsInView.get(PresentationStateReader.TAG_PR_ROTATION));
      actionsInView.put(ActionW.FLIP.cmd(), actionsInView.get(PresentationStateReader.TAG_PR_FLIP));
    }

    Double zoom = (Double) actionsInView.get(PRManager.TAG_PR_ZOOM);
    // Special Cases: -200.0 => best fit, -100.0 => real world size
    if (zoom != null && MathUtil.isDifferent(zoom, -200.0) && MathUtil.isDifferent(zoom, -100.0)) {
      actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, ZoomType.CURRENT);
      zoom(zoom);
    } else if (zoom != null) {
      actionsInView.put(
          ViewCanvas.ZOOM_TYPE_CMD,
          MathUtil.isEqual(zoom, -100.0) ? ZoomType.REAL : ZoomType.BEST_FIT);
      zoom(0.0);
    } else if (changePixConfig || spatialTransformation) {
      zoom(0.0);
    }

    ((DefaultViewModel) getViewModel()).setEnableViewModelChangeListeners(true);
    imageLayer.setEnableDispOperations(true);
    eventManager.updateComponentsListener(this);
  }

  public void updateKOButtonVisibleState() {

    Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(getSeries());
    boolean koElementExist = !koElements.isEmpty();
    // TODO try a given parameter so it wouldn't have to be computed again
    boolean needToRepaint = false;

    for (ViewButton vb : getViewButtons()) {
      if (vb != null && ActionW.KO_SELECTION.getTitle().equals(vb.getName())) {
        if (vb.isVisible() != koElementExist) {
          vb.setVisible(koElementExist);
          // repaint(getExtendedActionsBound());
          needToRepaint = true;
        }
        break;
      }
    }

    if (koStarButton.isVisible() != koElementExist) {
      koStarButton.setVisible(koElementExist);
      needToRepaint = true;
    }

    if (koElementExist) {
      needToRepaint = updateKOSelectedState(getImage());
    }

    if (needToRepaint) {
      // Required to update KO bar (toggle button state)
      if (eventManager instanceof EventManager manager) {
        manager.updateKeyObjectComponentsListener(this);
      }
      repaint();
    }
  }

  /**
   * @param img the DicomImageElement value
   * @return true if the state has changed and if the view or at least the KO button need to be
   *     repainted
   */
  protected boolean updateKOSelectedState(DicomImageElement img) {

    eState previousState = koStarButton.getState();

    // evaluate koSelection status for every Image change
    KOViewButton.eState newSelectionState = eState.UNSELECTED;

    Object selectedKO = getActionValue(ActionW.KO_SELECTION.cmd());

    if (img != null) {
      String sopInstanceUID = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
      String seriesInstanceUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);

      if (sopInstanceUID != null && seriesInstanceUID != null) {
        Integer frame = TagD.getTagValue(img, Tag.InstanceNumber, Integer.class);
        if (selectedKO instanceof KOSpecialElement koElement) {
          if (koElement.containsSopInstanceUIDReference(seriesInstanceUID, sopInstanceUID, frame)) {
            newSelectionState = eState.SELECTED;
          }
        } else {
          Collection<KOSpecialElement> koElements = DicomModel.getKoSpecialElements(getSeries());
          for (KOSpecialElement koElement : koElements) {
            if (koElement.containsSopInstanceUIDReference(
                seriesInstanceUID, sopInstanceUID, frame)) {
              newSelectionState = eState.EXIST;
              break;
            }
          }
        }
      }
    }

    koStarButton.setState(newSelectionState);

    Boolean selected = koStarButton.getState().equals(eState.SELECTED);
    actionsInView.put(ActionW.KO_TOGGLE_STATE.cmd(), selected);

    return previousState != newSelectionState;
  }

  @Override
  public void setSeries(MediaSeries<DicomImageElement> series, DicomImageElement selectedDicom) {
    super.setSeries(series, selectedDicom);

    // TODO
    // JFrame frame = new JFrame();
    // frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    // JPanel pane = new JPanel();

    // layeredPane.setPreferredSize(new Dimension(200, 200));
    // pane.remove(layeredPane);
    // layeredPane.removeAll();
    // panner.setSize(200, 200);
    // layeredPane.add(panner, JLayeredPane.DEFAULT_LAYER);
    // pane.add(layeredPane);
    // panner.setBounds(0, 0, 200, 200);
    // pane.setBounds(0, 0, 200, 200);
    // frame.add(pane);
    // frame.pack();
    // frame.setVisible(true);

    if (series != null) {
      AuditLog.LOGGER.info(
          "open:series nb:{} modality:{}",
          series.getSeriesNumber(),
          TagD.getTagValue(series, Tag.Modality));
    }

    updateKOButtonVisibleState();
  }

  @Override
  protected void setImage(DicomImageElement img) {
    boolean newImg = img != null && !img.equals(imageLayer.getSourceImage());
    if (newImg) {
      deletePrLayers();
      PrGraphicUtil.applyPresentationModel(img);
    }
    super.setImage(img);

    if (newImg) {
      updateSegmentation(img);
      updatePrButtonState(img);
      updateKOSelectedState(img);
    }
  }

  @Override
  public void reset() {
    super.reset();
    if (getActionValue(ActionW.PR_STATE.cmd()) instanceof PRSpecialElement pr) {
      setPresentationState(pr, true);
    }
  }

  private void deletePrLayers() {
    // Delete previous PR Layers
    List<GraphicLayer> dcmLayers =
        (List<GraphicLayer>) actionsInView.get(PRManager.TAG_DICOM_LAYERS);
    if (dcmLayers != null) {
      // Prefer to delete by type because layer uid can change
      for (GraphicLayer layer : dcmLayers) {
        graphicManager.deleteByLayer(layer);
      }
      actionsInView.remove(PRManager.TAG_DICOM_LAYERS);
    }
  }

  void updatePR() {
    DicomImageElement img = imageLayer.getSourceImage();
    if (img != null) {
      updatePrButtonState(img);
    }
  }

  private synchronized void updatePrButtonState(DicomImageElement img) {
    Object oldPR = getActionValue(ActionW.PR_STATE.cmd());
    ViewButton prButton = PRManager.buildPrSelection(this, series, img);
    getViewButtons().removeIf(b -> b == null || ActionW.PR_STATE.getTitle().equals(b.getName()));
    if (prButton != null) {
      prButton.setVisible(true);
      getViewButtons().add(prButton);
    } else if (oldPR instanceof PRSpecialElement) {
      setPresentationState(null, true);
      actionsInView.put(ActionW.PR_STATE.cmd(), oldPR);
    } else if (ActionState.NoneLabel.NONE.equals(oldPR)) {
      // No persistence for NONE
      actionsInView.put(ActionW.PR_STATE.cmd(), null);
    }
  }

  public void updateSegmentation() {
    updateSegmentation(imageLayer.getSourceImage());
  }

  private void updateSegmentation(DicomImageElement img) {
    graphicManager.deleteByLayerType(LayerType.DICOM_SEG);
    if (series != null && img != null) {
      String patientPseudoUID = DicomModel.getPatientPseudoUID(series);
      List<SpecialElementRegion> segList =
          HiddenSeriesManager.getHiddenElementsFromPatient(
              SpecialElementRegion.class, patientPseudoUID);
      if (!segList.isEmpty()) {
        Set<SegContour> contours = new LinkedHashSet<>();
        for (SpecialElementRegion seg : segList) {
          if (seg.isVisible() && seg.containsSopInstanceUIDReference(img)) {
            Set<LazyContourLoader> loaders = seg.getContours(img);
            if (loaders != null && !loaders.isEmpty()) {
              for (LazyContourLoader lazyLoader : loaders) {
                try {
                  Set<SegContour> c = lazyLoader.getLazyContours();
                  if (c != null && !c.isEmpty()) {
                    contours.addAll(c);
                  }
                } catch (Exception e) {
                  LOGGER.error("Error loading contours", e);
                }
              }
            }
          }
        }

        for (SegContour c : contours) {
          // Structure graphics
          Graphic graphic = c.getSegGraphic();
          if (graphic != null) {
            for (PropertyChangeListener listener : graphicManager.getGraphicsListeners()) {
              graphic.addPropertyChangeListener(listener);
            }
            graphicManager.addGraphic(graphic);
          }
        }
      }
    }
  }

  protected void sortStack(Comparator<DicomImageElement> sortComparator) {
    if (sortComparator != null) {
      // Only refresh UI components, Fix WEA-222
      eventManager.updateComponentsListener(this);
    }
  }

  @Override
  protected void computeCrosslines(double location) {
    DicomImageElement image = this.getImage();
    if (image != null) {
      GeometryOfSlice sliceGeometry = image.getSliceGeometry();
      if (sliceGeometry != null && sliceGeometry.isRowColumnOrthogonal()) {
        ViewCanvas<DicomImageElement> view2DPane = eventManager.getSelectedViewPane();
        MediaSeries<DicomImageElement> selSeries =
            view2DPane == null ? null : view2DPane.getSeries();
        if (selSeries != null) {
          // Get the current image of the selected Series
          DicomImageElement selImage = view2DPane.getImage();
          // Get the first and the last image of the selected Series according to Slice Location

          DicomImageElement firstImage = null;
          DicomImageElement lastImage = null;
          double min = Double.MAX_VALUE;
          double max = -Double.MAX_VALUE;
          final Iterable<DicomImageElement> list =
              selSeries.getMedias(
                  (Filter<DicomImageElement>)
                      view2DPane.getActionValue(ActionW.FILTERED_SERIES.cmd()),
                  getCurrentSortComparator());
          synchronized (selSeries) {
            for (DicomImageElement dcm : list) {
              double[] loc = (double[]) dcm.getTagValue(TagW.SlicePosition);
              if (loc != null) {
                double position = loc[0] + loc[1] + loc[2];
                if (min > position) {
                  min = position;
                  firstImage = dcm;
                }
                if (max < position) {
                  max = position;
                  lastImage = dcm;
                }
              }
            }
          }

          GraphicLayer layer = AbstractGraphicModel.getOrBuildLayer(this, LayerType.CROSSLINES);
          // IntersectSlice: display a line representing the center of the slice
          IntersectSlice slice = new IntersectSlice(sliceGeometry);
          if (firstImage != null && firstImage != lastImage) {
            addCrossline(firstImage, layer, slice, Color.cyan);
          }
          if (lastImage != null && firstImage != lastImage) {
            addCrossline(lastImage, layer, slice, Color.cyan);
          }
          if (selImage != null) {
            // IntersectVolume: display a rectangle to show the slice thickness
            if (!addCrossline(selImage, layer, new IntersectVolume(sliceGeometry), Color.blue)) {
              // When the volume limits are outside the image, get only the intersection
              addCrossline(selImage, layer, slice, Color.blue);
            }
          }
          repaint();
        }
      }
    }
  }

  public List<Point2D> getCrossLine(GeometryOfSlice sliceGeometry, LocalizerPoster localizer) {
    if (sliceGeometry != null && localizer != null && sliceGeometry.isRowColumnOrthogonal()) {
      List<Point2D> pts = localizer.getOutlineOnLocalizerForThisGeometry(sliceGeometry);
      if (pts != null && !pts.isEmpty()) {
        int lastPointIndex = pts.size() - 1;
        if (lastPointIndex > 0) {
          Point2D checkPoint = pts.get(lastPointIndex);
          if (Objects.equals(checkPoint, pts.get(--lastPointIndex))) {
            pts.remove(lastPointIndex);
          }
        }
        return pts;
      }
    }
    return null;
  }

  protected boolean addCrossline(
      DicomImageElement selImage, GraphicLayer layer, LocalizerPoster localizer, Color color) {
    GeometryOfSlice sliceGeometry = selImage.getSliceGeometry();
    List<Point2D> pts = getCrossLine(sliceGeometry, localizer);
    if (pts != null) {
      try {
        Graphic graphic;
        if (pts.size() == 2) {
          graphic = new LineGraphic().buildGraphic(pts);
        } else {
          graphic = new PolygonGraphic().buildGraphic(pts);
        }
        graphic.setPaint(color);
        graphic.setLabelVisible(Boolean.FALSE);
        graphic.setLayer(layer);

        graphicManager.addGraphic(graphic);
        return true;
      } catch (InvalidShapeException e) {
        LOGGER.error("Building crossline", e);
      }
    }
    return false;
  }

  @Override
  public synchronized void enableMouseAndKeyListener(MouseActions actions) {
    super.enableMouseAndKeyListener(actions);
    if (lens != null) {
      lens.enableMouseListener();
    }
  }

  public MouseActionAdapter getMouseAdapter(String command) {
    if (command.equals(ActionW.CONTEXTMENU.cmd())) {
      return contextMenuHandler;
    } else if (command.equals(ActionW.WINLEVEL.cmd())) {
      return getAction(ActionW.LEVEL);
    }

    Optional<Feature<? extends ActionState>> actionKey = eventManager.getActionKey(command);
    if (actionKey.isEmpty()) {
      return null;
    }

    if (actionKey.get().isDrawingAction()) {
      return graphicMouseHandler;
    }

    Optional<? extends ActionState> actionState = eventManager.getAction(actionKey.get());
    if (actionState.isPresent() && actionState.get() instanceof MouseActionAdapter listener) {
      return listener;
    }
    return null;
  }

  public boolean isAutoCenter(Point2D p) {
    Point2D pt = getClipViewCoordinatesOffset();
    Rectangle2D bounds = new Rectangle2D.Double(-pt.getX(), -pt.getY(), getWidth(), getHeight());
    GeomUtil.growRectangle(bounds, -10);
    Shape path = inverseTransform.createTransformedShape(bounds);
    if (path instanceof Path2D path2D) {
      return !path2D.contains(p);
    }
    return !bounds.contains(p);
  }

  public void computeCrosshair(Vector3d p3, PanPoint panPoint) {
    DicomImageElement image = this.getImage();
    if (image != null) {
      boolean released = panPoint.getState() == State.DRAGEND;
      if (!released) {
        graphicManager.deleteByLayerType(LayerType.CROSSLINES);
      }
      GeometryOfSlice sliceGeometry = image.getSliceGeometry();
      if (sliceGeometry != null) {
        Plane plane = this.getPlane();
        if (plane != null && p3 != null) {
          Point2D p = sliceGeometry.getImagePosition(p3);
          if (p != null) {
            Vector3d dim = sliceGeometry.getDimensions();
            boolean axial = Plane.AXIAL.equals(plane);
            Point2D centerPt = new Point2D.Double(p.getX(), p.getY());
            int centerGap =
                eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_CENTER_GAP, 40);
            GraphicLayer layer = AbstractGraphicModel.getOrBuildLayer(this, LayerType.CROSSLINES);
            if (released) {
              int mode = eventManager.getOptions().getIntProperty(View2d.P_CROSSHAIR_MODE, 1);
              if (mode == 2 || mode == 1 && isAutoCenter(p)) {
                setCenter(p.getX() - dim.y * 0.5, p.getY() - dim.x * 0.5);
              }
            } else {
              List<Point2D> pts = new ArrayList<>();
              pts.add(new Point2D.Double(p.getX(), -50.0));
              pts.add(new Point2D.Double(p.getX(), dim.x + 50));

              boolean sagittal = Plane.SAGITTAL.equals(plane);
              Color color1 = axial ? Biped.A.getColor() : Biped.F.getColor();
              addCrosshairLine(layer, pts, color1, centerPt, centerGap);

              List<Point2D> pts2 = new ArrayList<>();
              Color color2 = sagittal ? Biped.A.getColor() : Biped.R.getColor();
              pts2.add(new Point2D.Double(-50.0, p.getY()));
              pts2.add(new Point2D.Double(dim.y + 50, p.getY()));
              addCrosshairLine(layer, pts2, color2, centerPt, centerGap);

              //            PlanarImage dispImg = image.getImage();
              //            if (dispImg != null) {
              //              Rectangle2D rect =
              //                  new Rectangle2D.Double(
              //                      0,
              //                      0,
              //                      dispImg.width() * image.getRescaleX(),
              //                      dispImg.height() * image.getRescaleY());
              //              addRectangle(layer, rect, axial ? Biped.F.getColor() : sagittal ?
              // Biped.R.getColor()  : Biped.A.getColor() );
              //            }
            }
          }
        }
      }
    }
  }

  protected void addCrosshairLine(
      GraphicLayer layer, List<Point2D> pts, Color color, Point2D center, int centerGap) {
    if (pts != null && !pts.isEmpty()) {
      try {
        Graphic graphic;
        if (pts.size() == 2) {
          LineWithGapGraphic line = new LineWithGapGraphic();
          line.setCenterGap(center);
          line.setGapSize(centerGap);
          graphic = line.buildGraphic(pts);
        } else {
          graphic = new PolygonGraphic().buildGraphic(pts);
        }
        graphic.setPaint(color);
        graphic.setLabelVisible(Boolean.FALSE);
        graphic.setLayer(layer);

        graphicManager.addGraphic(graphic);
      } catch (InvalidShapeException e) {
        LOGGER.error("Add crosshair line", e);
      }
    }
  }

  protected void addRectangle(GraphicLayer layer, Rectangle2D rect, Color color) {
    if (rect != null && layer != null) {
      try {
        Graphic graphic = new RectangleGraphic().buildGraphic(rect);
        graphic.setPaint(color);
        graphic.setLabelVisible(Boolean.FALSE);
        graphic.setFilled(Boolean.FALSE);
        graphic.setLayer(layer);

        graphicManager.addGraphic(graphic);

      } catch (InvalidShapeException e) {
        LOGGER.error("Add rectangle", e);
      }
    }
  }

  public Plane getPlane() {
    Plane plane = null;
    MediaSeries<DicomImageElement> s = getSeries();
    if (s != null) {
      Object img = s.getMedia(MediaSeries.MEDIA_POSITION.MIDDLE, null, null);
      if (img instanceof DicomImageElement imageElement) {
        Plan orientation = ImageOrientation.getPlan(imageElement);
        if (orientation != null) {
          if (Plan.AXIAL.equals(orientation)) {
            plane = Plane.AXIAL;
          } else if (Plan.CORONAL.equals(orientation)) {
            plane = Plane.CORONAL;
          } else if (Plan.SAGITTAL.equals(orientation)) {
            plane = Plane.SAGITTAL;
          }
        }
      }
    }
    return plane;
  }

  @Override
  public void resetMouseAdapter() {
    super.resetMouseAdapter();

    // reset context menu that is a field of this instance
    contextMenuHandler.setButtonMaskEx(0);
    graphicMouseHandler.setButtonMaskEx(0);
  }

  @Override
  protected void fillPixelInfo(
      final PixelInfo pixelInfo, final DicomImageElement imageElement, final double[] c) {
    if (c != null && c.length >= 1) {
      WlPresentation wlp = null;
      WindowOp wlOp = (WindowOp) getDisplayOpManager().getNode(WindowOp.OP_NAME);
      if (wlOp != null) {
        wlp = wlOp.getWlPresentation();
      }
      for (int i = 0; i < c.length; i++) {
        c[i] = imageElement.pixelToRealValue(c[i], wlp).doubleValue();
      }
      pixelInfo.setValues(c);
    }
  }

  @Override
  public void handleLayerChanged(ImageLayer layer) {
    repaint();
  }

  @Override
  public void focusGained(FocusEvent e) {
    if (!e.isTemporary()) {
      ImageViewerPlugin<DicomImageElement> pane = eventManager.getSelectedView2dContainer();
      if (pane != null && pane.isContainingView(this)) {
        pane.setSelectedImagePaneFromFocus(this);
      }
    }
  }

  public boolean hasValidContent() {
    return getSourceImage() != null;
  }

  public JPopupMenu buildGraphicContextMenu(final MouseEvent evt, final List<Graphic> selected) {
    if (selected != null) {
      final JPopupMenu popupMenu = new JPopupMenu();
      TitleMenuItem itemTitle = new TitleMenuItem(Messages.getString("View2d.selection"));
      popupMenu.add(itemTitle);
      popupMenu.addSeparator();
      boolean graphicComplete = true;
      if (selected.size() == 1) {
        final Graphic graph = selected.get(0);
        if (graph instanceof final DragGraphic dragGraphic) {
          if (!dragGraphic.isGraphicComplete()) {
            graphicComplete = false;
          }
          if (dragGraphic.getVariablePointsNumber()) {
            if (graphicComplete) {
              /*
               * Convert mouse event point to real image coordinate point (without geometric
               * transformation)
               */
              final MouseEventDouble mouseEvt =
                  new MouseEventDouble(
                      View2d.this,
                      MouseEvent.MOUSE_RELEASED,
                      evt.getWhen(),
                      16,
                      0,
                      0,
                      0,
                      0,
                      1,
                      true,
                      1);
              mouseEvt.setSource(View2d.this);
              mouseEvt.setImageCoordinates(getImageCoordinatesFromMouse(evt.getX(), evt.getY()));
              final int ptIndex = dragGraphic.getHandlePointIndex(mouseEvt);
              if (ptIndex >= 0) {
                JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.rmv_pt"));
                menuItem.addActionListener(e -> dragGraphic.removeHandlePoint(ptIndex, mouseEvt));
                popupMenu.add(menuItem);

                menuItem = new JMenuItem(Messages.getString("View2d.draw_pt"));
                menuItem.addActionListener(
                    e -> {
                      dragGraphic.forceToAddPoints(ptIndex);
                      MouseEventDouble evt2 =
                          new MouseEventDouble(
                              View2d.this,
                              MouseEvent.MOUSE_PRESSED,
                              evt.getWhen(),
                              16,
                              evt.getX(),
                              evt.getY(),
                              evt.getXOnScreen(),
                              evt.getYOnScreen(),
                              1,
                              true,
                              1);
                      graphicMouseHandler.mousePressed(evt2);
                    });
                popupMenu.add(menuItem);
                popupMenu.add(new JSeparator());
              }
            } else if (graphicMouseHandler.getDragSequence() != null
                && Objects.equals(dragGraphic.getPtsNumber(), Graphic.UNDEFINED)) {
              final JMenuItem item2 = new JMenuItem(Messages.getString("View2d.stop_draw"));
              item2.addActionListener(
                  e -> {
                    MouseEventDouble event =
                        new MouseEventDouble(View2d.this, 0, 0, 16, 0, 0, 0, 0, 2, true, 1);
                    graphicMouseHandler.getDragSequence().completeDrag(event);
                    graphicMouseHandler.mouseReleased(event);
                  });
              popupMenu.add(item2);
              popupMenu.add(new JSeparator());
            }
          }
        }
      }

      if (graphicComplete) {
        JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.delete_sel"));
        menuItem.addActionListener(
            e -> View2d.this.getGraphicManager().deleteSelectedGraphics(View2d.this, true));
        popupMenu.add(menuItem);

        menuItem = new JMenuItem(Messages.getString("View2d.cut"));
        menuItem.addActionListener(
            e -> {
              DefaultView2d.GRAPHIC_CLIPBOARD.setGraphics(selected);
              View2d.this.getGraphicManager().deleteSelectedGraphics(View2d.this, false);
            });
        popupMenu.add(menuItem);
        menuItem = new JMenuItem(Messages.getString("View2d.copy"));
        menuItem.addActionListener(e -> DefaultView2d.GRAPHIC_CLIPBOARD.setGraphics(selected));
        popupMenu.add(menuItem);
        popupMenu.add(new JSeparator());
      }

      // TODO separate AbstractDragGraphic and ClassGraphic for properties
      final ArrayList<DragGraphic> list = new ArrayList<>();
      for (Graphic graphic : selected) {
        if (graphic instanceof DragGraphic dragGraphic) {
          list.add(dragGraphic);
        }
      }

      if (selected.size() == 1) {
        final Graphic graph = selected.get(0);
        JMenuItem item = new JMenuItem(Messages.getString("View2d.to_front"));
        item.addActionListener(e -> graph.toFront());
        popupMenu.add(item);
        item = new JMenuItem(Messages.getString("View2d.to_back"));
        item.addActionListener(e -> graph.toBack());
        popupMenu.add(item);
        popupMenu.add(new JSeparator());

        if (graphicComplete && graph instanceof LineGraphic lineGraphic) {

          final JMenuItem calibMenu = new JMenuItem(Messages.getString("View2d.chg_calib"));
          calibMenu.addActionListener(
              e -> {
                String title = Messages.getString("View2d.clibration");
                CalibrationView calibrationDialog =
                    new CalibrationView(lineGraphic, View2d.this, true);
                ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2d.this);
                int res =
                    JOptionPane.showConfirmDialog(
                        ColorLayerUI.getContentPane(layer),
                        calibrationDialog,
                        title,
                        JOptionPane.OK_CANCEL_OPTION);
                if (layer != null) {
                  layer.hideUI();
                }
                if (res == JOptionPane.OK_OPTION) {
                  calibrationDialog.applyNewCalibration();
                }
              });
          popupMenu.add(calibMenu);
          popupMenu.add(new JSeparator());
        }
      }

      if (!list.isEmpty()) {
        JMenuItem properties = new JMenuItem(Messages.getString("View2d.draw_prop"));
        properties.addActionListener(
            e -> {
              ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(View2d.this);
              JDialog dialog = new MeasureDialog(View2d.this, list);
              ColorLayerUI.showCenterScreen(dialog, layer);
            });
        popupMenu.add(properties);
      }
      return popupMenu;
    }
    return null;
  }

  public JPopupMenu buildContextMenu(final MouseEvent evt) {
    JPopupMenu popupMenu = buildLeftMouseActionMenu();
    int count = popupMenu.getComponentCount();

    if (DefaultView2d.GRAPHIC_CLIPBOARD.hasGraphics()) {
      JMenuItem menuItem = new JMenuItem(Messages.getString("View2d.paste_draw"));
      menuItem.addActionListener(e -> copyGraphicsFromClipboard());
      popupMenu.add(menuItem);
    }
    count = addSeparatorToPopupMenu(popupMenu, count);

    if (eventManager instanceof EventManager manager) {
      GuiUtils.addItemToMenu(popupMenu, manager.getPresetMenu("weasis.contextmenu.presets"));
      GuiUtils.addItemToMenu(popupMenu, manager.getLutShapeMenu("weasis.contextmenu.lutShape"));
      GuiUtils.addItemToMenu(popupMenu, manager.getLutMenu("weasis.contextmenu.lut"));
      GuiUtils.addItemToMenu(popupMenu, manager.getLutInverseMenu("weasis.contextmenu.invertLut"));
      GuiUtils.addItemToMenu(popupMenu, manager.getFilterMenu("weasis.contextmenu.filter"));
      count = addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getZoomMenu("weasis.contextmenu.zoom"));
      GuiUtils.addItemToMenu(
          popupMenu, manager.getOrientationMenu("weasis.contextmenu.orientation"));
      GuiUtils.addItemToMenu(popupMenu, manager.getCineMenu("weasis.contextmenu.cine"));
      GuiUtils.addItemToMenu(popupMenu, manager.getSortStackMenu("weasis.contextmenu.sortstack"));
      addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getResetMenu("weasis.contextmenu.reset"));
    }

    String pClose = "weasis.contextmenu.close";
    if (LangUtil.getNULLtoTrue((Boolean) actionsInView.get(pClose))
        && GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(pClose, true)) {
      JMenuItem close = new JMenuItem(Messages.getString("View2d.close"));
      close.addActionListener(e -> View2d.this.setSeries(null, null));
      popupMenu.add(close);
    }
    return popupMenu;
  }
}
