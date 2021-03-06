/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.list;

import java.nio.file.Path;

import javax.swing.JList;

import org.weasis.base.explorer.list.AThumbnailModel;
import org.weasis.core.api.media.data.MediaElement;

@SuppressWarnings({ "serial" })
public class AcquireThumbnailModel<E extends MediaElement> extends AThumbnailModel<E> {

    public AcquireThumbnailModel(JList<E> list) {
        super(list);
    }

    @Override
    public void setData(Path dir) {
        if (this.loading) {
            return;
        }

        if (dir != null) {
            synchronized (this) {
                this.loading = true;
            }
            this.list.getSelectionModel().setValueIsAdjusting(true);
            this.list.requestFocusInWindow();
            loadContent(dir);
            synchronized (this) {
                fireContentsChanged(this, 0, getSize() - 1);
                this.loading = false;
            }
            this.list.getSelectionModel().setValueIsAdjusting(false);
        }
    }

}
