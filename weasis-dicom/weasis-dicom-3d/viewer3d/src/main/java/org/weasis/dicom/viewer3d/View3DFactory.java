/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.Threading;
import java.awt.Component;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JOptionPane;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer3d.vr.OpenglUtils;

@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class)
public class View3DFactory implements SeriesViewerFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(View3DFactory.class);

  public static final String NAME = "3D Viewer";

  public static final String P_OPENGL_ENABLE = "opengl.enable";
  public static final String P_OPENGL_PREV_INIT = "opengl.prev.init";

  private static OpenGLInfo openGLInfo;

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(ActionIcon.VOLUME_RENDERING);
  }

  @Override
  public String getUIName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return NAME;
  }

  @Override
  public SeriesViewer createSeriesViewer(Map<String, Object> properties) {
    if (isOpenglEnable()) {
      // GridBagLayoutModel model = View3DContainer.LAYOUT_LIST.get(0);
      GridBagLayoutModel model = View3DContainer.VIEWS_vr;
      String uid = null;
      if (properties != null) {
        Object obj = properties.get(org.weasis.core.api.image.GridBagLayoutModel.class.getName());
        if (obj instanceof GridBagLayoutModel gridBagLayoutModel) {
          model = gridBagLayoutModel;
        } else {
          obj = properties.get(ViewCanvas.class.getName());
          if (obj instanceof Integer intVal) {
            Optional<ComboItemListener<GridBagLayoutModel>> layout =
                EventManager.getInstance().getAction(ActionW.LAYOUT);
            if (layout.isPresent()) {
              model = ImageViewerPlugin.getBestDefaultViewLayout(layout.get(), intVal);
            }
          }
        }

        // Set UID
        Object val = properties.get(ViewerPluginBuilder.UID);
        if (val instanceof String s) {
          uid = s;
        }
      }
      View3DContainer instance = new View3DContainer(model, uid, getUIName(), getIcon(), null);
      if (properties != null) {
        Object obj = properties.get(DataExplorerModel.class.getName());
        if (obj instanceof DicomModel m) {
          // Register the PropertyChangeListener
          m.addPropertyChangeListener(instance);
        }
      }

      return instance;
    }

    showOpenglErrorMessage(UIManager.BASE_AREA);
    return null;
  }

  public static int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
    int val = 0;
    if (layout != null && defaultClass != null) {
      Iterator<LayoutConstraints> enumVal = layout.getConstraints().keySet().iterator();
      while (enumVal.hasNext()) {
        try {
          Class<?> clazz = Class.forName(enumVal.next().getType());
          if (defaultClass.isAssignableFrom(clazz)) {
            val++;
          }
        } catch (Exception e) {
          LOGGER.error("Checking view", e);
        }
      }
    }
    return val;
  }

  public static void closeSeriesViewer(View3DContainer view3dContainer) {
    // Unregister the PropertyChangeListener
    DataExplorerView dicomView = UIManager.getExplorerPlugin(DicomExplorer.NAME);
    if (dicomView != null) {
      dicomView.getDataExplorerModel().removePropertyChangeListener(view3dContainer);
    }
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return DicomMediaIO.SERIES_MIMETYPE.equals(mimeType);
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer viewer) {
    if (viewer instanceof View3DContainer) {
      return true;
    }
    return false;
  }

  @Override
  public int getLevel() {
    return 10;
  }

  @Override
  public boolean canAddSeries() {
    return false;
  }

  @Override
  public boolean canExternalizeSeries() {
    return true;
  }

  @Override
  public List<Action> getOpenActions() {
    return null;
  }

  public static OpenGLInfo getOpenGLInfo() {
    if (openGLInfo == null && isOpenglEnable()) {
      BundleTools.LOCAL_UI_PERSISTENCE.putBooleanProperty(P_OPENGL_PREV_INIT, false);
      // Thread wait for initialization
      Threading.invoke(true, () -> initOpenGLInfo(), null);
    }
    return openGLInfo;
  }

  private static void initOpenGLInfo() {
    try {
      long startTime = System.currentTimeMillis();
      LOGGER.info("Checking 3D capabilities");
      GLContext glContext = OpenglUtils.getDefaultGlContext();
      glContext.makeCurrent();
      GL var2 = glContext.getGL();
      int[] textMax = new int[1];
      var2.glGetIntegerv(GL2.GL_MAX_3D_TEXTURE_SIZE, textMax, 0);
      openGLInfo =
          new OpenGLInfo(
              var2.glGetString(GL.GL_VERSION),
              glContext.getGLVersion(),
              var2.glGetString(GL.GL_VENDOR),
              var2.glGetString(GL.GL_RENDERER),
              textMax[0]);
      glContext.release();
      LOGGER.info(
          "{} 3D initialization time: {} ms",
          AuditLog.MARKER_PERF,
          (System.currentTimeMillis() - startTime));
      BundleTools.LOCAL_UI_PERSISTENCE.putBooleanProperty(P_OPENGL_PREV_INIT, true);
      LOGGER.info(
          "Video card for OpenGL: {}, {} {}",
          openGLInfo.vendor(),
          openGLInfo.renderer(),
          openGLInfo.version());
    } catch (Exception e) {
      BundleTools.LOCAL_UI_PERSISTENCE.putBooleanProperty(P_OPENGL_ENABLE, false);
      BundleTools.LOCAL_UI_PERSISTENCE.putBooleanProperty(P_OPENGL_PREV_INIT, false);
      LOGGER.error("Cannot init OpenGL", e);
    }
  }

  public static boolean isOpenglEnable() {
    return BundleTools.LOCAL_UI_PERSISTENCE.getBooleanProperty(P_OPENGL_ENABLE, true);
  }

  public static void showOpenglErrorMessage(Component parent) {
    String msg = Messages.getString("View3DFactory.opengl.error.msg");
    JOptionPane.showMessageDialog(parent, msg);
  }

  // ================================================================================
  // OSGI service implementation
  // ================================================================================

  @Activate
  protected void activate(ComponentContext context) throws Exception {
    LOGGER.info("3D Viewer is activated");
    System.setProperty("jogl.1thread", "worker"); // TODO set to auto

    WProperties prefs = BundleTools.LOCAL_UI_PERSISTENCE;
    if (BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.force.3d", false)) {
      prefs.putBooleanProperty(P_OPENGL_PREV_INIT, true);
      prefs.putBooleanProperty(P_OPENGL_ENABLE, true);
    }

    boolean openglPrevInit = prefs.getBooleanProperty(P_OPENGL_PREV_INIT, true);
    if (!openglPrevInit && isOpenglEnable()) {
      LOGGER.info("Do not enable OpenGL because of the last initialization has failed.");
      prefs.putBooleanProperty(P_OPENGL_ENABLE, false);
    }

    if (isOpenglEnable()) {
      getOpenGLInfo();
    }
  }

  @Deactivate
  protected void deactivate(ComponentContext context) {
    LOGGER.info("3D Viewer is deactivated");
  }
}
