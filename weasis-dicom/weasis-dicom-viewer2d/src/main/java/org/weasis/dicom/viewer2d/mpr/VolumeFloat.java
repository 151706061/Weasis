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

public final class VolumeFloat extends Volume<Float, float[]> {

  public VolumeFloat(int sizeX, int sizeY, int sizeZ, int channels, JProgressBar progressBar) {
    super(sizeX, sizeY, sizeZ, CvType.CV_32FC(channels), progressBar);
  }

  public VolumeFloat(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  @Override
  protected Float initMinValue() {
    return -Float.MAX_VALUE;
  }

  @Override
  protected Float initMaxValue() {
    return Float.MAX_VALUE;
  }

  public VolumeFloat(
      Volume<Float, float[]> volume, int sizeX, int sizeY, int sizeZ, Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    checkSingleChannel(channels);
    return CvType.CV_32F;
  }

  @Override
  protected ChunkedArray<float[]> createChunkedArray(long totalElements) {
    checkSingleChannel(channels);
    return ChunkedArray.ofFloat(totalElements);
  }

  @Override
  protected void setElementInData(long index, Float value) {
    data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)] = value;
  }

  @Override
  protected Float getElementFromData(long index) {
    return data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)];
  }

  @Override
  protected void setChannelValues(long baseIndex, Voxel<Float> voxel) {
    Float val = voxel.getValue(0);
    if (val != null) {
      setElementInData(baseIndex, val);
    }
  }

  @Override
  protected void copyFrom(PlanarImage image, int sliceIndex, Matrix4d transform, Dimension dim) {
    float[] pixelData = new float[dim.width * dim.height];
    image.get(0, 0, pixelData);

    if (isIdentityTransform(transform)) {
      long destOffset = (long) sliceIndex * size.y * size.x;
      if (data != null) {
        data.copyFrom(destOffset, pixelData, 0, pixelData.length);
      } else {
        long byteOffset = destOffset * byteDepth;
        for (int i = 0; i < pixelData.length; i++) {
          mappedBuffer.putFloat(byteOffset + (long) i * byteDepth, pixelData[i]);
        }
      }
    } else {
      copyPixels(
          dim,
          (x, y) -> {
            float val = pixelData[y * dim.width + x];
            Vector3i coord = mapSliceToVolumeCoordinates(x, y, sliceIndex, transform);
            if (!isOutside(coord.x, coord.y, coord.z)) {
              long index = (long) coord.z * size.y * size.x + (long) coord.y * size.x + coord.x;
              if (data != null) {
                setElementInData(index, val);
              } else {
                mappedBuffer.putFloat(index * byteDepth, val);
              }
            }
          });
    }
  }

  public void readVolume(DataInputStream stream, int x, int y, int z) throws IOException {
    Float val = stream.readFloat();
    setValue(x, y, z, val, null);
  }

  public void writeVolume(DataOutputStream stream, int x, int y, int z) throws IOException {
    Float val = getValue(x, y, z, 0);
    if (val == null) {
      throw new IOException("Null voxel value at (" + x + "," + y + "," + z + ")");
    }
    stream.writeFloat(val);
  }
}
