/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.awt.Dimension;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import javax.swing.JProgressBar;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.opencv.core.CvType;
import org.weasis.opencv.data.PlanarImage;

public final class VolumeDouble extends Volume<Double, double[]> {

  public VolumeDouble(int sizeX, int sizeY, int sizeZ, int channels, JProgressBar progressBar) {
    super(sizeX, sizeY, sizeZ, CvType.CV_64FC(channels), progressBar);
  }

  public VolumeDouble(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  @Override
  protected Double initMinValue() {
    return -Double.MAX_VALUE;
  }

  @Override
  protected Double initMaxValue() {
    return Double.MAX_VALUE;
  }

  public VolumeDouble(
      Volume<Double, double[]> volume, int sizeX, int sizeY, int sizeZ, Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    checkSingleChannel(channels);
    return CvType.CV_64F;
  }

  @Override
  protected ChunkedArray<double[]> createChunkedArray(long totalElements) {
    checkSingleChannel(channels);
    return ChunkedArray.ofDouble(totalElements);
  }

  @Override
  protected void setElementInData(long index, Double value) {
    data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)] = value;
  }

  @Override
  protected Double getElementFromData(long index) {
    return data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)];
  }

  @Override
  protected void setChannelValues(long baseIndex, Voxel<Double> voxel) {
    Double val = voxel.getValue(0);
    if (val != null) {
      setElementInData(baseIndex, val);
    }
  }

  @Override
  protected void copyFrom(PlanarImage image, int sliceIndex, Matrix4d transform, Dimension dim) {
    double[] pixelData = new double[dim.width * dim.height];
    image.get(0, 0, pixelData);

    if (isIdentityTransform(transform)) {
      long destOffset = (long) sliceIndex * size.y * size.x;
      if (data != null) {
        data.copyFrom(destOffset, pixelData, 0, pixelData.length);
      } else {
        long byteOffset = destOffset * byteDepth;
        for (int i = 0; i < pixelData.length; i++) {
          mappedBuffer.putDouble(byteOffset + (long) i * byteDepth, pixelData[i]);
        }
      }
    } else {
      copyPixels(
          dim,
          (x, y) -> {
            double val = pixelData[y * dim.width + x];
            Vector3i coord = mapSliceToVolumeCoordinates(x, y, sliceIndex, transform);
            if (!isOutside(coord.x, coord.y, coord.z)) {
              long index = (long) coord.z * size.y * size.x + (long) coord.y * size.x + coord.x;
              if (data != null) {
                setElementInData(index, val);
              } else {
                mappedBuffer.putDouble(index * byteDepth, val);
              }
            }
          });
    }
  }

  public void readVolume(DataInputStream stream, int x, int y, int z) throws IOException {
    Double val = stream.readDouble();
    setValue(x, y, z, val, null);
  }

  public void writeVolume(DataOutputStream stream, int x, int y, int z) throws IOException {
    Double val = getValue(x, y, z, 0);
    if (val == null) {
      throw new IOException("Null voxel value at (" + x + "," + y + "," + z + ")");
    }
    stream.writeDouble(val);
  }
}
