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
package org.weasis.core.ui.model.graphic.imp;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.weasis.core.api.gui.util.GeomUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.AbstractDragGraphic;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.bean.AdvancedShape;
import org.weasis.core.ui.model.utils.bean.AdvancedShape.BasicShape;
import org.weasis.core.ui.model.utils.bean.AdvancedShape.ScaleInvariantShape;
import org.weasis.core.ui.model.utils.exceptions.InvalidShapeException;
import org.weasis.core.ui.model.utils.imp.DefaultGraphicLabel;
import org.weasis.core.ui.serialize.RectangleAdapter;
import org.weasis.core.ui.util.MouseEventDouble;

@XmlType(name = "annotation")
@XmlRootElement(name = "annotation")
@XmlAccessorType(XmlAccessType.NONE)
public class AnnotationGraphic extends AbstractDragGraphic {
    private static final long serialVersionUID = -6993299250389257151L;

    public static final Integer POINTS_NUMBER = 2;
    public static final Icon ICON = new ImageIcon(AnnotationGraphic.class.getResource("/icon/22x22/draw-text.png")); //$NON-NLS-1$

    protected Point2D ptBox;
    protected Point2D ptAnchor; // Let AB be a simple a line segment
    protected String[] labels;
    protected Boolean lineABvalid; // estimate if line segment is valid or not
    protected Rectangle2D labelBounds;
    protected Double labelWidth;
    protected Double labelHeight;

    public AnnotationGraphic() {
        super(POINTS_NUMBER);
    }

    public AnnotationGraphic(AnnotationGraphic annotationGraphic) {
        super(annotationGraphic);
    }

    @Override
    protected void initCopy(Graphic graphic) {
        super.initCopy(graphic);
        if (graphic instanceof AnnotationGraphic) {
            AnnotationGraphic annotationGraphic = (AnnotationGraphic) graphic;
            labelBounds = Optional.ofNullable(annotationGraphic.labelBounds).map(lb -> lb.getBounds2D()).orElse(null);
            labelWidth = annotationGraphic.labelWidth;
            labelHeight = annotationGraphic.labelHeight;
            if (annotationGraphic.labels != null)
                labels = annotationGraphic.labels.clone();
        }
    }

    @Override
    public AnnotationGraphic copy() {
        return new AnnotationGraphic(this);
    }

    @Override
    protected void prepareShape() throws InvalidShapeException {
        if (!isShapeValid()) {
            throw new InvalidShapeException("This shape cannot be drawn"); //$NON-NLS-1$
        }
       // Do not build shape as labelBounds can be initialize only by the method setLabel()
    }

    protected void setHandlePointList(Point2D.Double ptAnchor, Point2D.Double ptBox) {
        if (ptBox == null && ptAnchor != null) {
            ptBox = ptAnchor;
        }
        if (ptBox != null && ptBox.equals(ptAnchor)) {
            ptAnchor = null;
        }
        setHandlePoint(0, ptAnchor == null ? null : (Point2D.Double) ptAnchor.clone());
        setHandlePoint(1, ptBox == null ? null : (Point2D.Double) ptBox.clone());
        buildShape(null);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getUIName() {
        return Messages.getString("Tools.Anno"); //$NON-NLS-1$
    }

    @XmlElementWrapper(name = "labels")
    @XmlElement(name = "label")
    public String[] getLabels() {
        return labels;
    }

    public void setLabels(String[] labels) {
        this.labels = labels;
    }

    @XmlElement(name = "labelBounds")
    @XmlJavaTypeAdapter(RectangleAdapter.Rectangle2DAdapter.class)
    public Rectangle2D getLabelBounds() {
        return labelBounds;
    }

    public void setLabelBounds(Rectangle2D labelBounds) {
        this.labelBounds = labelBounds;
    }

    @XmlAttribute(name = "labelWidth")
    public Double getLabelWidth() {
        return labelWidth;
    }

    public void setLabelWidth(Double labelWidth) {
        this.labelWidth = labelWidth;
    }

    @XmlAttribute(name = "labelHeight")
    public Double getLabelHeight() {
        return labelHeight;
    }

    public void setLabelHeight(Double labelHeight) {
        this.labelHeight = labelHeight;
    }

    @Override
    public void updateLabel(Object source, ViewCanvas<?> view2d, Point2D pos) {
        setLabel(labels, view2d, pos);
    }

    @Override
    public void updateLabel(Object source, ViewCanvas<?> view2d) {
        setLabel(labels, view2d);
    }

    @Override
    public void buildShape(MouseEventDouble mouseEvent) {
        updateTool();
        AdvancedShape newShape = null;

        if (ptBox != null) {
            ViewCanvas<?> view = getDefaultView2d(mouseEvent);
            if (labels == null) {
                if (view != null) {
                    setLabel(new String[] { getInitialText(view) }, view, ptBox);
                    // call buildShape
                    return;
                }
                if (labelHeight == 0 || labelWidth == 0) {
                    // This graphic cannot be displayed, remove it.
                    fireRemoveAction();
                    return;
                }
            }
            newShape = new AdvancedShape(this, 2);
            Line2D line = null;
            if (lineABvalid) {
                line = new Line2D.Double(ptBox, ptAnchor);
            }
            labelBounds = new Rectangle.Double();
            labelBounds.setFrameFromCenter(ptBox.getX(), ptBox.getY(),
                ptBox.getX() + labelWidth / 2 + DefaultGraphicLabel.GROWING_BOUND,
                ptBox.getY() + labelHeight * labels.length / 2 + DefaultGraphicLabel.GROWING_BOUND);
            GeomUtil.growRectangle(labelBounds, DefaultGraphicLabel.GROWING_BOUND);
            if (line != null) {
                newShape.addLinkSegmentToInvariantShape(line, ptBox, labelBounds, getDashStroke(lineThickness), false);

                ScaleInvariantShape arrow = newShape.addScaleInvShape(GeomUtil.getArrowShape(ptAnchor, ptBox, 15, 8),
                    ptAnchor, getStroke(lineThickness), false);
                arrow.setFilled(true);
            }
            newShape.addAllInvShape(labelBounds, ptBox, getStroke(lineThickness), false);

        }

        setShape(newShape, mouseEvent);
    }

    @Override
    public int getKeyCode() {
        return KeyEvent.VK_B;
    }

    @Override
    public int getModifier() {
        return 0;
    }

    protected void updateTool() {
        ptAnchor = getHandlePoint(0);
        ptBox = getHandlePoint(1);

        lineABvalid = ptAnchor != null && !ptAnchor.equals(ptBox);
    }

    protected String getInitialText(ViewCanvas<?> view) {
        return Messages.getString("AnnotationGraphic.text_box"); //$NON-NLS-1$
    }

    @Override
    public void paintLabel(Graphics2D g2d, AffineTransform transform) {
        if (labelVisible && labels != null && labelBounds != null) {
            Paint oldPaint = g2d.getPaint();

            Rectangle2D rect = labelBounds;
            Point2D pt = new Point2D.Double(rect.getCenterX(), rect.getCenterY());
            if (transform != null) {
                transform.transform(pt, pt);
            }

            float px = (float) (pt.getX() - rect.getWidth() / 2 + DefaultGraphicLabel.GROWING_BOUND);
            float py = (float) (pt.getY() - rect.getHeight() / 2 + DefaultGraphicLabel.GROWING_BOUND);

            for (String label : labels) {
                if (StringUtil.hasText(label)) {
                    py += labelHeight;
                    DefaultGraphicLabel.paintColorFontOutline(g2d, label, px, py, Color.WHITE);
                }
            }
            g2d.setPaint(oldPaint);
        }
    }

    @Override
    public Area getArea(AffineTransform transform) {
        if (shape == null) {
            return new Area();
        }
        if (shape instanceof AdvancedShape) {
            AdvancedShape s = (AdvancedShape) shape;
            Area area = s.getArea(transform);
            List<BasicShape> list = s.getShapeList();
            if (!list.isEmpty()) {
                BasicShape b = list.get(list.size() - 1);
                // Allow to move inside the box, not only around stroke.
                area.add(new Area(b.getRealShape()));
            }

            return area;
        } else {
            return super.getArea(transform);
        }
    }

    public Point2D getAnchorPoint() {
        updateTool();
        return ptAnchor == null ? null : (Point2D) ptAnchor.clone();
    }

    public Point2D getBoxPoint() {
        updateTool();
        return ptBox == null ? null : (Point2D) ptBox.clone();
    }

    protected void reset() {
        labels = null;
        labelBounds = null;
        labelHeight = labelWidth = 0d;
    }

    @Override
    public void setLabel(String[] labels, ViewCanvas<?> view2d) {
        Point2D pt = getBoxPoint();
        if (pt == null) {
            pt = getAnchorPoint();
        }
        if (pt != null) {
            this.setLabel(labels, view2d, pt);
        }
    }

    @Override
    public void setLabel(String[] labels, ViewCanvas<?> view2d, Point2D pos) {
        if (view2d == null || labels == null || labels.length == 0 || pos == null) {
            reset();
        } else {
            Graphics2D g2d = (Graphics2D) view2d.getJComponent().getGraphics();
            if (g2d == null) {
                return;
            }
            this.labels = labels;
            Font defaultFont = g2d.getFont();
            FontRenderContext fontRenderContext =
                ((Graphics2D) view2d.getJComponent().getGraphics()).getFontRenderContext();

            updateBoundsSize(defaultFont, fontRenderContext);

            labelBounds = new Rectangle.Double();
            labelBounds.setFrameFromCenter(pos.getX(), pos.getY(), (labelWidth + DefaultGraphicLabel.GROWING_BOUND) / 2,
                ((labelHeight * labels.length) + DefaultGraphicLabel.GROWING_BOUND) * 2);
            labelBounds.setFrameFromCenter(pos.getX(), pos.getY(),
                ptBox.getX() + labelWidth / 2 + DefaultGraphicLabel.GROWING_BOUND,
                ptBox.getY() + labelHeight * this.labels.length / 2 + DefaultGraphicLabel.GROWING_BOUND);
            GeomUtil.growRectangle(labelBounds, DefaultGraphicLabel.GROWING_BOUND);
        }
        buildShape(null);
    }

    protected void updateBoundsSize(Font defaultFont, FontRenderContext fontRenderContext) {
        Optional.ofNullable(defaultFont).orElseThrow(() -> new RuntimeException("Font should not be null"));
        Optional.ofNullable(fontRenderContext)
            .orElseThrow(() -> new RuntimeException("FontRenderContext should not be null"));

        if (labels == null || labels.length == 0) {
            reset();
        } else {
            double maxWidth = 0;
            for (String label : labels) {
                if (StringUtil.hasText(label)) {
                    TextLayout layout = new TextLayout(label, defaultFont, fontRenderContext);
                    maxWidth = Math.max(layout.getBounds().getWidth(), maxWidth);
                }
            }
            labelHeight = new TextLayout("Tg", defaultFont, fontRenderContext).getBounds().getHeight() + 2; //$NON-NLS-1$
            labelWidth = maxWidth;
        }
    }
}
