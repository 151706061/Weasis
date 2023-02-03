/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JPopupMenu;
import javax.swing.ToolTipManager;
import javax.swing.border.Border;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.ContextMenuHandler;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.DefaultView2d.ZoomType;
import org.weasis.core.ui.editor.image.FocusHandler;
import org.weasis.core.ui.editor.image.GraphicMouseHandler;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.Panner;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.ui.model.utils.bean.PanPoint.State;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer3d.ActionVol;
import org.weasis.dicom.viewer3d.InfoLayer3d;
import org.weasis.dicom.viewer3d.geometry.Camera;
import org.weasis.dicom.viewer3d.geometry.Controls;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutShape;

public class View3d extends VolumeCanvas
    implements ViewCanvas<DicomImageElement>,
        RenderingLayerChangeListener<DicomImageElement>,
        GLEventListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(View3d.class);

  public enum ViewType {
    AXIAL,
    CORONAL,
    SAGITTAL,
    VOLUME3D
  };

  static final float[] vertexBufferData =
      new float[] {
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        -1.0f, 1.0f,
        1.0f, -1.0f,
        1.0f, 1.0f
      };

  protected final Border focusBorder =
      BorderFactory.createMatteBorder(1, 1, 1, 1, IconColor.ACTIONS_YELLOW.getColor());
  protected final Border viewBorder = BorderFactory.createMatteBorder(1, 1, 1, 1, Color.GRAY);

  protected final FocusHandler<DicomImageElement> focusHandler;
  protected final GraphicMouseHandler<DicomImageElement> graphicMouseHandler;
  private final PanPoint highlightedPosition = new PanPoint(State.CENTER);
  private final PanPoint startedDragPoint = new PanPoint(State.DRAGSTART);
  private int pointerType = 0;
  private LayerAnnotation infoLayer;
  protected final ContextMenuHandler contextMenuHandler;

  private DicomVolTexture volTexture;
  private final ComputeTexture texture;
  private final Program program;
  private final Program quadProgram;
  protected final Controls controls;
  protected final RenderingLayer renderingLayer;
  private final ImageViewerEventManager<DicomImageElement> eventManager;

  private int vertexBuffer;
  protected Preset volumePreset;
  private ViewType viewType;
  private double zoomFactor;

  public View3d(
      ImageViewerEventManager<DicomImageElement> eventManager, DicomVolTexture volTexture) {
    super(null);
    this.eventManager = eventManager;
    this.texture = new ComputeTexture(this, ComputeTexture.COMPUTE_LOCAL_SIZE);
    this.quadProgram =
        new Program("basic", ShaderManager.VERTEX_SHADER, ShaderManager.FRAGMENT_SHADER);
    this.program = new Program("compute", ShaderManager.COMPUTE_SHADER);
    this.controls = new Controls(this);

    this.zoomFactor = -1.0;
    try {
      setSharedContext(OpenglUtils.getDefaultGlContext());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    setLayout(null);

    this.volumePreset = Preset.basicPresets.get(4);
    volumePreset.setRequiredBuilding(true);
    volumePreset.setRenderer(this);

    this.volTexture = volTexture;
    this.renderingLayer = new RenderingLayer();

    actionsInView.put(ActionVol.ORIENTATION_CUBE.cmd(), false);
    actionsInView.put(ActionVol.HIDE_CROSSHAIR_CENTER.cmd(), true);
    actionsInView.put(ActionVol.RECENTERING_CROSSHAIR.cmd(), false);

    addMouseWheelListener(controls);
    addMouseMotionListener(controls);
    addMouseListener(controls);

    this.infoLayer = new InfoLayer3d(this);

    initActionWState();

    this.graphicMouseHandler = new GraphicMouseHandler(this);
    this.contextMenuHandler = new ContextMenuHandler(this);
    this.focusHandler = new FocusHandler(this);
    setFocusable(true);

    setBorder(viewBorder);
    setPreferredSize(new Dimension(4096, 4096));
    setMinimumSize(new Dimension(50, 50));

    addGLEventListener(this);
  }

  protected void initActionWState() {
    // set unit when texture load (middle image)
    actionsInView.put(ActionW.SPATIAL_UNIT.cmd(), Unit.PIXEL);
    actionsInView.put(ZOOM_TYPE_CMD, ZoomType.BEST_FIT);
    actionsInView.put(ActionW.ZOOM.cmd(), 0.0);
    actionsInView.put(ActionW.LENS.cmd(), false);
    actionsInView.put(ActionW.DRAWINGS.cmd(), true);
    actionsInView.put(LayerType.CROSSLINES.name(), true);
    actionsInView.put(ActionW.INVERSE_STACK.cmd(), false);
    actionsInView.put(ActionW.FILTERED_SERIES.cmd(), null);

    actionsInView.put(ActionW.INVERT_LUT.cmd(), false);
    actionsInView.put(ActionW.ROTATION.cmd(), 0);
    actionsInView.put(ActionW.FLIP.cmd(), false);

    actionsInView.put(ActionVol.RENDERING_TYPE.cmd(), RenderingType.COMPOSITE);
    actionsInView.put(ActionVol.MIP_DEPTH.cmd(), 2);
  }

  @Override
  public void registerDefaultListeners() {
    addFocusListener(this);
    ToolTipManager.sharedInstance().registerComponent(this);
    renderingLayer.addLayerChangeListener(this);

    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            Double currentZoom = (Double) actionsInView.get(ActionW.ZOOM.cmd());
            /*
             * Negative value means a default value according to the zoom type (pixel size, best fit...). Set again
             * to default value to compute again the position. For instance, the image cannot be center aligned
             * until the view has been repaint once (because the size is null).
             */
            if (currentZoom <= 0.0) {
              zoom(0.0);
            }
            repaint();
          }
        });
  }

  @Override
  public void disposeView() {
    disableMouseAndKeyListener();
    removeFocusListener(this);
    ToolTipManager.sharedInstance().unregisterComponent(this);
    renderingLayer.removeLayerChangeListener(this);
    if (volTexture != null) {
      UIManager.closeSeries(volTexture.getSeries());
    }
    GL2 gl2 = OpenglUtils.getGL2();
    program.destroy(gl2);
    quadProgram.destroy(gl2);
    texture.destroy(gl2);
    super.disposeView();
  }

  @Override
  public void handleLayerChanged(RenderingLayer<DicomImageElement> layer) {
    display();
  }

  public RenderingLayer getRenderingLayer() {
    return renderingLayer;
  }

  public DicomVolTexture getVolTexture() {
    return volTexture;
  }

  public boolean isReadyForRendering() {
    return volTexture != null && volTexture.isReadyForDisplay();
  }

  public Camera getCamera() {
    return camera;
  }

  public Controls getControls() {
    return controls;
  }

  public ViewType getViewType() {
    return viewType;
  }

  public void setViewType(ViewType viewType) {
    this.viewType = viewType;
  }

  public double getZoom() {
    return zoomFactor;
  }

  public double getActualDisplayZoom() {
    return zoomFactor < 0.0
        ? (double) Math.min(getWidth(), getHeight())
            / (double) getVolTexture().getMaxDimensionLength()
        : zoomFactor;
  }

  public void setZoom(double var1) {
    setZoom(var1, true);
  }

  public void setZoom(double var1, boolean var3) {
    this.zoomFactor = var1;
    if (var3) {
      repaint();
    }
  }

  public void changeZoom(double var1) {
    this.zoomFactor += var1;
    repaint();
  }

  public void setVolTexture(DicomVolTexture volTexture) {
    this.volTexture = volTexture;
    if (volTexture != null) {
      if (viewType == ViewType.VOLUME3D) {
        int quality =
            Math.min(
                8192, (int) Math.round(volTexture.getDepth() * volTexture.getTexelSize().z) * 2);
        renderingLayer.setQuality(quality);
        eventManager.getAction(ActionVol.VOL_QUALITY).get().setSliderValue(quality, false);
        setVolumePreset(volumePreset);
      } else {
        display();
      }
    }
  }

  @Override
  protected void paintComponent(Graphics graphs) {
    super.paintComponent(graphs);
    if (graphs instanceof Graphics2D graphics2D) {
      draw(graphics2D);
    }
  }

  protected void draw(Graphics2D g2d) {
    Stroke oldStroke = g2d.getStroke();
    Paint oldColor = g2d.getPaint();

    Font defaultFont = getFont();
    g2d.setFont(defaultFont);

    Point2D p = getClipViewCoordinatesOffset();
    g2d.translate(p.getX(), p.getY());
    drawLayers(g2d, affineTransform, inverseTransform);
    g2d.translate(-p.getX(), -p.getY());

    drawPointer(g2d);
    //   drawAffineInvariant(g2d);
    if (infoLayer != null) {
      g2d.setFont(getLayerFont());
      infoLayer.paint(g2d);
    }
    // drawOnTop(g2d);

    g2d.setFont(defaultFont);
    g2d.setPaint(oldColor);
    g2d.setStroke(oldStroke);
  }

  @Override
  public void init(GLAutoDrawable var1) {
    initShaders(var1.getGL().getGL2());
  }

  public void initShaders(GL2 gl2) {
    // TODO ad custom background
    gl2.glClearColor(0, 0, 0.0f, 1);

    program.init(gl2);
    program.allocateUniform(
        gl2,
        "viewMatrix",
        (gl, loc) ->
            gl.glUniformMatrix4fv(
                loc,
                1,
                false,
                camera.getViewMatrix().invert().get(Buffers.newDirectFloatBuffer(16))));
    program.allocateUniform(
        gl2,
        "projectionMatrix",
        (gl, loc) ->
            gl.glUniformMatrix4fv(
                loc,
                1,
                false,
                camera.getProjectionMatrix().invert().get(Buffers.newDirectFloatBuffer(16))));
    program.allocateUniform(
        gl2,
        "depthSampleNumber",
        (gl, loc) -> {
          gl2.glUniform1i(loc, renderingLayer.getDepthSampleNumber());
        });
    program.allocateUniform(
        gl2,
        "lutShape",
        (gl, loc) -> {
          gl2.glUniform1i(loc, renderingLayer.getLutShapeId());
        });

    program.allocateUniform(
        gl2,
        "backgroundColor",
        (gl, loc) -> {
          gl.glUniform3fv(loc, 1, new Vector3f(0, 0, 0).get(Buffers.newDirectFloatBuffer(3)));
        });

    for (int i = 0; i < 4; ++i) {
      int val = i;
      program.allocateUniform(
          gl2,
          String.format("lights[%d].position", val),
          (gl, loc) -> {
            gl.glUniform4fv(loc, 1, camera.getRayOrigin().get(Buffers.newDirectFloatBuffer(4)));
          });
      program.allocateUniform(
          gl2,
          String.format("lights[%d].diffuse", val),
          (gl, loc) -> {
            gl.glUniform4fv(
                loc,
                1,
                new Vector4f(
                        volumePreset.getDiffuse(),
                        volumePreset.getDiffuse(),
                        volumePreset.getDiffuse(),
                        1)
                    .get(Buffers.newDirectFloatBuffer(4)));
          });
      program.allocateUniform(
          gl2,
          String.format("lights[%d].specular", val),
          (gl, loc) -> {
            gl.glUniform4fv(
                loc,
                1,
                new Vector4f(
                        volumePreset.getSpecular(),
                        volumePreset.getSpecular(),
                        volumePreset.getSpecular(),
                        1)
                    .get(Buffers.newDirectFloatBuffer(4)));
          });
      program.allocateUniform(
          gl2,
          String.format("lights[%d].ambient", val),
          (gl, loc) -> {
            gl.glUniform4fv(
                loc,
                1,
                new Vector4f(
                        volumePreset.getAmbient(),
                        volumePreset.getAmbient(),
                        volumePreset.getAmbient(),
                        1)
                    .get(Buffers.newDirectFloatBuffer(4)));
          });
      program.allocateUniform(
          gl2,
          String.format("lights[%d].enabled", val),
          (gl, loc) -> {
            gl.glUniform1i(loc, val < 1 ? 1 : 0);
          });
    }
    program.allocateUniform(
        gl2,
        "lightColor",
        (gl, loc) -> {
          gl.glUniform3fv(
              loc, 1, new Vector3f(1.0f, 1.0f, 1.0f).get(Buffers.newDirectFloatBuffer(3)));
        });
    program.allocateUniform(
        gl2,
        "shading",
        (gl, loc) -> {
          gl.glUniform1i(loc, renderingLayer.isShading() ? 1 : 0);
        });
    program.allocateUniform(
        gl2,
        "texelSize",
        (gl, loc) -> {
          gl.glUniform3fv(
              loc, 1, volTexture.getNormalizedTexelSize().get(Buffers.newDirectFloatBuffer(3)));
        });

    program.allocateUniform(
        gl2,
        "renderingType",
        (gl, loc) -> {
          gl.glUniform1i(loc, renderingLayer.getRenderingType().getId());
        });
    program.allocateUniform(
        gl2,
        "volTexture",
        (gl, loc) -> {
          gl.glUniform1i(loc, 0);
        });
    program.allocateUniform(
        gl2,
        "colorMap",
        (gl, loc) -> {
          gl.glUniform1i(loc, 1);
        });
    program.allocateUniform(
        gl2,
        "textureDataType",
        (gl, loc) -> {
          gl.glUniform1i(loc, TextureData.getDataType(volTexture.getPixelFormat()));
        });

    program.allocateUniform(
        gl2,
        "opacityFactor",
        (gl, loc) -> {
          gl.glUniform1f(loc, 1f); // FIXME from 0.01 to 2
        });
    program.allocateUniform(
        gl2,
        "inputLevelMin",
        (gl, loc) -> {
          gl.glUniform1f(loc, volTexture.getLevelMin());
        });
    program.allocateUniform(
        gl2,
        "inputLevelMax",
        (gl, loc) -> {
          gl.glUniform1f(loc, volTexture.getLevelMax());
        });
    program.allocateUniform(
        gl2,
        "outputLevelMin",
        (gl, loc) -> {
          gl.glUniform1f(loc, volumePreset.getColorMin());
        });
    program.allocateUniform(
        gl2,
        "outputLevelMax",
        (gl, loc) -> {
          gl.glUniform1f(loc, volumePreset.getColorMax());
        });
    program.allocateUniform(
        gl2,
        "windowWidth",
        (gl, loc) -> {
          gl.glUniform1f(loc, renderingLayer.getWindowWidth());
        });
    program.allocateUniform(
        gl2,
        "windowCenter",
        (gl, loc) -> {
          gl.glUniform1f(loc, renderingLayer.getWindowCenter());
        });

    final IntBuffer intBuffer = IntBuffer.allocate(1);

    texture.init(gl2);
    volTexture.init(gl2);
    if (volumePreset != null) {
      volumePreset.init(gl2);
    }

    quadProgram.init(gl2);
    gl2.glGenBuffers(1, intBuffer);
    vertexBuffer = intBuffer.get(0);
    gl2.glBindBuffer(GL.GL_ARRAY_BUFFER, vertexBuffer);
    gl2.glBufferData(
        GL.GL_ARRAY_BUFFER,
        vertexBufferData.length * Float.BYTES,
        Buffers.newDirectFloatBuffer(vertexBufferData),
        GL.GL_STATIC_DRAW);
  }

  public void display(GLAutoDrawable drawable) {
    render(drawable.getGL().getGL2());
  }

  private void render(GL2 gl2) {
    gl2.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
    if (volTexture.isReadyForDisplay()) {
      int sampleCount = renderingLayer.getQuality();
      if (camera.isAdjusting()) {
        sampleCount = Math.min(512, Math.max(64, sampleCount / 4));
      }
      renderingLayer.setDepthSampleNumber(sampleCount);
      program.use(gl2);
      program.setUniforms(gl2);
      volTexture.render(gl2);
      if (volumePreset != null) {
        volumePreset.render(gl2);
      }
      texture.render(gl2);
      quadProgram.use(gl2);

      gl2.glEnable(GL.GL_BLEND);
      gl2.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

      gl2.glEnableVertexAttribArray(0);
      gl2.glBindBuffer(GL.GL_ARRAY_BUFFER, vertexBuffer);
      gl2.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 0, 0);
      gl2.glActiveTexture(GL.GL_TEXTURE0);
      gl2.glBindTexture(GL.GL_TEXTURE_2D, texture.getId());
      gl2.glDrawArrays(GL.GL_TRIANGLES, 0, vertexBufferData.length / 2);
      gl2.glDisableVertexAttribArray(0);
      gl2.glDisable(GL.GL_BLEND);
    }
  }

  public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
    GL2 gl2 = drawable.getGL().getGL2();
    gl2.glViewport(0, 0, width, height);
    camera.resetTransformation();
  }

  public void dispose(GLAutoDrawable drawable) {
    GL2 gl2 = drawable.getGL().getGL2();
    // FIXME destroy when release of cache
    //    if (volTexture != null) {
    //      volTexture.destroy(gl2);
    //    }
  }

  public void setVolumePreset(Preset volumePreset) {
    this.volumePreset = Objects.requireNonNull(volumePreset);
    volumePreset.setRenderer(this);
    volumePreset.setRequiredBuilding(true);

    getVolTexture().getPresetList(true).stream()
        .filter(p -> p.getKeyCode() == 0x30)
        .findFirst()
        .ifPresent(
            p -> {
              setPresetWindowLevel(p, false);
              eventManager
                  .getAction(ActionW.PRESET)
                  .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(p));
              eventManager
                  .getAction(ActionW.LUT_SHAPE)
                  .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(p.getLutShape()));
              eventManager
                  .getAction(ActionW.LEVEL)
                  .ifPresent(a -> a.setRealValue(p.getLevel(), false));
              eventManager
                  .getAction(ActionW.WINDOW)
                  .ifPresent(a -> a.setRealValue(p.getWindow(), false));
            });

    display();
  }

  public Preset getVolumePreset() {
    return volumePreset;
  }

  @Override
  public void copyActionWState(HashMap<String, Object> actionsInView) {}

  @Override
  public ImageViewerEventManager<DicomImageElement> getEventManager() {
    return eventManager;
  }

  @Override
  public void updateSynchState() {}

  @Override
  public PixelInfo getPixelInfo(Point p) {
    return null;
  }

  @Override
  public Panner<DicomImageElement> getPanner() {
    return null;
  }

  @Override
  public void setSeries(MediaSeries<DicomImageElement> series) {
    // Do nothing, use addSeries instead
  }

  @Override
  public void setSeries(MediaSeries<DicomImageElement> newSeries, DicomImageElement selectedMedia) {
    // Do nothing, use addSeries instead
  }

  @Override
  public void setFocused(Boolean focused) {
    MediaSeries<DicomImageElement> series = getSeries();
    if (series != null) {
      series.setFocused(focused);
    }
    if (focused && getBorder() == viewBorder) {
      setBorder(focusBorder);
    } else if (!focused && getBorder() == focusBorder) {
      setBorder(viewBorder);
    }
  }

  @Override
  public double getRealWorldViewScale() {
    return 0;
  }

  @Override
  public LayerAnnotation getInfoLayer() {
    return null;
  }

  @Override
  public int getTileOffset() {
    return 0;
  }

  @Override
  public void setTileOffset(int tileOffset) {}

  @Override
  public void center() {}

  @Override
  public void setCenter(Double modelOffsetX, Double modelOffsetY) {}

  @Override
  public void moveOrigin(PanPoint point) {}

  @Override
  public Comparator<DicomImageElement> getCurrentSortComparator() {
    return null;
  }

  @Override
  public void setSelected(Boolean selected) {}

  @Override
  public Font getLayerFont() {
    Font font = FontItem.DEFAULT_SEMIBOLD.getFont();
    return DefaultView2d.getLayerFont(getFontMetrics(font), getWidth());
  }

  @Override
  public void setDrawingsVisibility(Boolean visible) {}

  @Override
  public Object getLensActionValue(String action) {
    return null;
  }

  @Override
  public void changeZoomInterpolation(Interpolation interpolation) {}

  @Override
  public OpManager getDisplayOpManager() {
    return null;
  }

  @Override
  public void disableMouseAndKeyListener() {}

  @Override
  public void iniDefaultMouseListener() {
    // focus listener is always on
    this.addMouseListener(focusHandler);
    this.addMouseMotionListener(focusHandler);
  }

  @Override
  public void iniDefaultKeyListener() {}

  @Override
  public int getPointerType() {
    return 0;
  }

  @Override
  public void setPointerType(int pointerType) {}

  @Override
  public void addPointerType(int i) {}

  @Override
  public void resetPointerType(int i) {}

  @Override
  public Point2D getHighlightedPosition() {
    return null;
  }

  private void drawPointer(Graphics2D g) {
    if (pointerType < 1) {
      return;
    }
    if ((pointerType & CENTER_POINTER) == CENTER_POINTER) {
      drawPointer(g, (getWidth() - 1) * 0.5, (getHeight() - 1) * 0.5);
    }
    if ((pointerType & HIGHLIGHTED_POINTER) == HIGHLIGHTED_POINTER
        && highlightedPosition.isHighlightedPosition()) {
      // Display the position in the center of the pixel (constant position even with a high zoom
      // factor)
      double offsetX =
          modelToViewLength(highlightedPosition.getX() + 0.5 - viewModel.getModelOffsetX());
      double offsetY =
          modelToViewLength(highlightedPosition.getY() + 0.5 - viewModel.getModelOffsetY());
      drawPointer(g, offsetX, offsetY);
    }
  }

  @Override
  public List<Action> getExportActions() {
    return null;
  }

  public MouseActionAdapter getMouseAdapter(String command) {
    if (command.equals(ActionW.CONTEXTMENU.cmd())) {
      return contextMenuHandler;
    } else if (command.equals(ActionW.WINLEVEL.cmd())) {
      return getAction(ActionW.LEVEL);
      //    } else if (command.equals(ActionW.CROSSHAIR.cmd())) {
      //      return crosshairAction;
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

  public void resetMouseAdapter() {
    ViewCanvas.super.resetMouseAdapter();

    // reset context menu that is a field of this instance
    contextMenuHandler.setButtonMaskEx(0);
    graphicMouseHandler.setButtonMaskEx(0);
  }

  @Override
  public void resetZoom() {}

  @Override
  public void resetPan() {}

  @Override
  public void reset() {}

  @Override
  public List<ViewButton> getViewButtons() {
    return Collections.emptyList();
  }

  @Override
  public void closeLens() {}

  @Override
  public void updateCanvas(boolean triggerViewModelChangeListeners) {}

  @Override
  public void updateGraphicSelectionListener(ImageViewerPlugin<DicomImageElement> viewerPlugin) {}

  @Override
  public boolean requiredTextAntialiasing() {
    return false;
  }

  @Override
  public JPopupMenu buildGraphicContextMenu(MouseEvent evt, List<Graphic> selected) {
    return null;
  }

  @Override
  public JPopupMenu buildContextMenu(MouseEvent evt) {
    return null;
  }

  @Override
  public boolean hasValidContent() {
    return volTexture != null && volTexture.isReadyForDisplay();
  }

  @Override
  public void focusGained(FocusEvent e) {}

  @Override
  public void focusLost(FocusEvent e) {}

  @Override
  public void keyTyped(KeyEvent e) {}

  @Override
  public void keyPressed(KeyEvent e) {}

  @Override
  public void keyReleased(KeyEvent e) {}

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    String propertyName = evt.getPropertyName();
    if (propertyName != null) {
      if (propertyName.equals(ActionW.SYNCH.cmd())) {
        propertyChange((SynchEvent) evt.getNewValue());
      }
    }
  }

  private void propertyChange(final SynchEvent synch) {
    {
      SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
      if (synchData != null && Mode.NONE.equals(synchData.getMode())) {
        return;
      }

      for (Entry<String, Object> entry : synch.getEvents().entrySet()) {
        String command = entry.getKey();
        final Object val = entry.getValue();
        if (synchData != null && !synchData.isActionEnable(command)) {
          continue;
        }
        if (command.equals(ActionVol.SCROLLING.cmd())) {
          if (getViewType() != ViewType.VOLUME3D) { // If its not a volumetric view
            //  setSlice((Integer) val);
          }
        } else if (command.equals(ActionW.WINDOW.cmd())) {
          renderingLayer.setWindowWidth(((Double) val).intValue());
        } else if (command.equals(ActionW.LEVEL.cmd())) {
          renderingLayer.setWindowCenter(((Double) val).intValue());
        } else if (command.equals(ActionW.PRESET.cmd())) {
          if (val instanceof PresetWindowLevel) {
            setPresetWindowLevel((PresetWindowLevel) val, true);
          } else if (val == null) {
            setActionsInView(ActionW.PRESET.cmd(), val, false);
          }
        } else if (command.equals(ActionW.LUT_SHAPE.cmd())) {
          if (val instanceof LutShape lutShape) {
            renderingLayer.setLutShape(lutShape);
          }
        } else if (command.equals(ActionW.ROTATION.cmd()) && val instanceof Integer) {
          setRotation((Integer) val);
          if (getViewType() != ViewType.VOLUME3D) { // If its not a volumetric view
          }
        } else if (command.equals(ActionW.RESET.cmd())) {
          reset();
        } else if (command.equals(ActionW.ZOOM.cmd())) {
          double value = (Double) val;
          // Special Cases: -200.0 => best fit, -100.0 => real world size
          if (value != -200.0 && value != -100.0) {
            zoom(value);
          } else {
            Object zoomType = actionsInView.get(ViewCanvas.ZOOM_TYPE_CMD);
            actionsInView.put(
                ViewCanvas.ZOOM_TYPE_CMD, value == -100.0 ? ZoomType.REAL : ZoomType.BEST_FIT);
            zoom(0.0);
            actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, zoomType);
          }
        } else if (command.equals(ActionW.PAN.cmd())) {
          if (val instanceof PanPoint) {
            moveOrigin((PanPoint) entry.getValue());
          }
        } else if (command.equals(ActionW.FLIP.cmd())) {
          actionsInView.put(ActionW.FLIP.cmd(), val);
          // LangUtil.getNULLtoFalse((Boolean) val);
          //   updateAffineTransform();
          repaint();
        } else if (command.equals(ActionVol.VOL_PRESET.cmd())) {
          //  setActionsInView(ActionVol.VOLUME_PRESET.cmd(), val);
          setVolumePreset((Preset) val);
        } else if (command.equals(ActionW.INVERT_LUT.cmd())) {
          // actionsInView.put(ActionW.INVERT_LUT.cmd(), inverse);
          repaint();
        } else if (command.equals(ActionW.SPATIAL_UNIT.cmd())) {
          actionsInView.put(command, val);

          // TODO update only measure and limit when selected view share graphics
          List<Graphic> list = this.getGraphicManager().getAllGraphics();
          for (Graphic graphic : list) {
            graphic.updateLabel(true, this);
          }
        } else if (command.equals(ActionVol.RENDERING_TYPE.cmd())) {
          if (val instanceof RenderingType type) {
            renderingLayer.setRenderingType(type);
          }
        } else if (command.equals(ActionVol.MIP_DEPTH.cmd())) {
          if (val instanceof Integer) {
            //    setMipDepth((Integer) val);
          }
        } else if (command.equals(ActionVol.VOL_QUALITY.cmd())) {
          if (val instanceof Integer quality) {
            renderingLayer.setQuality(quality);
          }
        } else if (command.equals(ActionVol.VOL_SLICING.cmd())) {
          setActionsInView(ActionVol.VOL_SLICING.cmd(), val, true);
        } else if (command.equals(ActionVol.VOL_SHADING.cmd())) {
          if (val instanceof Boolean shading) {
            renderingLayer.setShading(shading);
          }
        }
      }
    }
  }

  private void setPresetWindowLevel(PresetWindowLevel preset, boolean repaint) {
    if (preset != null) {
      renderingLayer.removeLayerChangeListener(this);
      if (preset.isAutoLevel()) {
        int ww = volumePreset.getColorMax() - volumePreset.getColorMin();
        renderingLayer.setWindowWidth(ww);
        renderingLayer.setWindowCenter(volumePreset.getColorMin() + ww / 2);
      } else {
        renderingLayer.setWindowWidth((int) preset.getWindow());
        renderingLayer.setWindowCenter((int) preset.getLevel());
      }
      renderingLayer.setLutShape(preset.getLutShape());
      renderingLayer.addLayerChangeListener(this);
      if (repaint) {
        renderingLayer.fireLayerChanged();
      }
    }
    setActionsInView(ActionW.PRESET.cmd(), preset, false);
  }

  @Override
  public MediaSeries<DicomImageElement> getSeries() {
    DicomVolTexture vol = volTexture;
    if (vol != null) {
      return vol.getSeries();
    }
    return null;
  }

  @Override
  public int getFrameIndex() {
    return 0;
  }

  @Override
  public void drawLayers(
      Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {}

  @Override
  public ImageLayer<DicomImageElement> getImageLayer() {
    return null;
  }

  @Override
  public MeasurableLayer getMeasurableLayer() {
    return null;
  }

  @Override
  public DicomImageElement getImage() {
    return null;
  }

  @Override
  public PlanarImage getSourceImage() {
    return null;
  }

  @Override
  public void handleLayerChanged(ImageLayer<DicomImageElement> layer) {}
}
