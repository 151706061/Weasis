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

public final class VolumeInt extends Volume<Integer, int[]> {

  public VolumeInt(int sizeX, int sizeY, int sizeZ, int channels, JProgressBar progressBar) {
    super(sizeX, sizeY, sizeZ, CvType.CV_32SC(channels), progressBar);
  }

  public VolumeInt(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  public VolumeInt(
      Volume<? extends Number, int[]> volume,
      int sizeX,
      int sizeY,
      int sizeZ,
      Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected Integer initMinValue() {
    return Integer.MIN_VALUE;
  }

  @Override
  protected Integer initMaxValue() {
    return Integer.MAX_VALUE;
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    if (!isSigned) {
      throw new IllegalArgumentException("Unsigned int type is not supported in OpenCV");
    }
    checkSingleChannel(channels);
    return CvType.CV_32S;
  }

  @Override
  protected ChunkedArray<int[]> createChunkedArray(long totalElements) {
    checkSingleChannel(channels);
    return ChunkedArray.ofInt(totalElements);
  }

  @Override
  protected void setElementInData(long index, Integer value) {
    data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)] = value;
  }

  @Override
  protected Integer getElementFromData(long index) {
    return data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)];
  }

  @Override
  protected void setChannelValues(long baseIndex, Voxel<Integer> voxel) {
    Integer val = voxel.getValue(0);
    if (val != null) {
      setElementInData(baseIndex, val);
    }
  }

  @Override
  protected void copyFrom(PlanarImage image, int sliceIndex, Matrix4d transform, Dimension dim) {
    int[] pixelData = new int[dim.width * dim.height];
    image.get(0, 0, pixelData);

    if (isIdentityTransform(transform)) {
      long destOffset = (long) sliceIndex * size.y * size.x;
      if (data != null) {
        data.copyFrom(destOffset, pixelData, 0, pixelData.length);
      } else {
        long byteOffset = destOffset * byteDepth;
        for (int i = 0; i < pixelData.length; i++) {
          mappedBuffer.putInt(byteOffset + (long) i * byteDepth, pixelData[i]);
        }
      }
    } else {
      copyPixels(
          dim,
          (x, y) -> {
            int val = pixelData[y * dim.width + x];
            Vector3i coord = mapSliceToVolumeCoordinates(x, y, sliceIndex, transform);
            if (!isOutside(coord.x, coord.y, coord.z)) {
              long index = (long) coord.z * size.y * size.x + (long) coord.y * size.x + coord.x;
              if (data != null) {
                setElementInData(index, val);
              } else {
                mappedBuffer.putInt(index * byteDepth, val);
              }
            }
          });
    }
  }

  public void readVolume(DataInputStream stream, int x, int y, int z) throws IOException {
    Integer val = stream.readInt();
    setValue(x, y, z, val, null);
  }

  public void writeVolume(DataOutputStream stream, int x, int y, int z) throws IOException {
    Integer val = getValue(x, y, z, 0);
    if (val == null) {
      throw new IOException("Null voxel value at (" + x + "," + y + "," + z + ")");
    }
    stream.writeInt(val);
  }
}
