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
package org.weasis.dicom.codec.macro;

import java.util.Collection;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;

public class Module {

    protected final Attributes dcmItems;

    public Module(Attributes dcmItems) {
        if (dcmItems == null) {
            throw new NullPointerException("dcmItems"); //$NON-NLS-1$
        }
        this.dcmItems = dcmItems;
    }

    public Attributes getAttributes() {
        return dcmItems;
    }

    protected void updateSequence(int tag, Module module) {

        Sequence oldSequence = dcmItems.getSequence(tag);

        if (module != null) {
            if (oldSequence != null) {
                oldSequence.clear(); // Allows to remove parents of Attributes
            }
            Attributes attributes = module.getAttributes();
            if (attributes != null) {
                Attributes parent = attributes.getParent();
                if (parent != null) {
                    // Copy attributes and set parent to null
                    attributes = new Attributes(attributes);
                }
                dcmItems.newSequence(tag, 1).add(attributes);
            }
        }
    }

    protected void updateSequence(int tag, Collection<? extends Module> modules) {

        Sequence oldSequence = dcmItems.getSequence(tag);

        if (modules != null && modules.size() > 0) {
            if (oldSequence != null) {
                oldSequence.clear(); // Allows to remove parents of Attributes
            }
            Sequence newSequence = dcmItems.newSequence(tag, modules.size());
            for (Module module : modules) {
                Attributes attributes = module.getAttributes();
                if (attributes != null) {
                    Attributes parent = attributes.getParent();
                    if (parent != null) {
                        // Copy attributes and set parent to null
                        attributes = new Attributes(attributes);
                    }
                    newSequence.add(attributes);
                }
            }
        }
    }
}
