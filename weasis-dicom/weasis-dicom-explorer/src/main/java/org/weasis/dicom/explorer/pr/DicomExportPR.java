/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pr;

import eu.essilab.lablib.checkboxtree.TreeCheckingModel;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Series;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomExport;
import org.weasis.dicom.explorer.DicomModel;

public class DicomExportPR extends DicomExport {

  private boolean containsPR;

  public DicomExportPR(Window parent, DicomModel dicomModel) {
    super(parent, dicomModel);
  }

  @Override
  protected void initializePages() {
    Hashtable<String, Object> properties = new Hashtable<>();
    properties.put(getDicomModel().getClass().getName(), getDicomModel());
    //        properties.put(getTreeModel().getClass().getName(), getTreeModel());
    //
    //        initTreeCheckingModel();
    //
    //        ArrayList<AbstractItemDialogPage> list = new ArrayList<>();
    //        DicomExportFactory factory = new SendDicomFactory();
    //        list.add((AbstractItemDialogPage) factory.createDicomExportPage(properties));
    //
    //        InsertableUtil.sortInsertable(list);
    //        for (AbstractItemDialogPage page : list) {
    //            pagesRoot.add(new DefaultMutableTreeNode(page));
    //        }

    iniTree();
  }

  protected void initTreeCheckingModel() {
    TreeCheckingModel checkingModel = getTreeModel().getCheckingModel();
    checkingModel.setCheckingMode(TreeCheckingModel.CheckingMode.PROPAGATE_PRESERVING_UNCHECK);

    List<DefaultMutableTreeNode> nodesToDelete = new ArrayList<>();

    if (GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME)
        instanceof DicomExplorer explorer) {

      Set<Series<?>> openSeriesSet = explorer.getSelectedPatientOpenSeries();
      Object rootNode = getTreeModel().getModel().getRoot();

      if (!openSeriesSet.isEmpty() && rootNode instanceof DefaultMutableTreeNode mutableTreeNode) {
        List<TreePath> selectedSeriesPathsList = new ArrayList<>();

        Enumeration<?> enumTreeNode = mutableTreeNode.breadthFirstEnumeration();
        while (enumTreeNode.hasMoreElements()) {
          Object child = enumTreeNode.nextElement();
          if (child instanceof DefaultMutableTreeNode treeNode) {
            if (treeNode.getLevel() != 3) { // 3 stands for Series Level
              continue;
            }

            Object userObject = treeNode.getUserObject();
            if (userObject instanceof DicomSeries) {
              if (!(((DicomSeries) userObject).getTagValue(TagD.get(Tag.Modality)).equals("PR"))) {
                nodesToDelete.add(treeNode);
              } else {
                selectedSeriesPathsList.add(new TreePath(treeNode.getPath()));
                containsPR = true;
              }
            }
          }
        }

        if (!selectedSeriesPathsList.isEmpty()) {
          TreePath[] seriesCheckingPaths = selectedSeriesPathsList.toArray(new TreePath[0]);
          checkingModel.setCheckingPaths(seriesCheckingPaths);
          getTreeModel().setDefaultSelectionPaths(selectedSeriesPathsList);
        }
        if (!nodesToDelete.isEmpty()) {
          for (DefaultMutableTreeNode node : nodesToDelete) {
            node.removeAllChildren();
            node.removeFromParent();
          }
        }
      }
    }
  }

  public boolean isContainsPR() {
    return containsPR;
  }
}
