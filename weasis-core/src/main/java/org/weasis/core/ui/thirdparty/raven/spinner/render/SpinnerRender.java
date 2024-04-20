package org.weasis.core.ui.thirdparty.raven.spinner.render;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;

public interface SpinnerRender {
    boolean isDisplayStringAble();

    boolean isPaintComplete();

    void paintCompleteIndeterminate(Graphics2D g2, Component component, Rectangle rec, float last,
        float f, float p);

    void paintIndeterminate(Graphics2D g2, Component component, Rectangle rec, float f);

    void paintDeterminate(Graphics2D g2, Component component, Rectangle rec, float p);

    int getInsets();
}
