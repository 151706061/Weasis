/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.weasis.core.ui.model.utils;

import org.weasis.core.ui.util.MouseEventDouble;

/**
 * The Interface DragSequence.
 *
 * @author Nicolas Roduit
 */
public interface Draggable {

    void startDrag(MouseEventDouble mouseevent);

    void drag(MouseEventDouble mouseevent);

    Boolean completeDrag(MouseEventDouble mouseevent);

}
