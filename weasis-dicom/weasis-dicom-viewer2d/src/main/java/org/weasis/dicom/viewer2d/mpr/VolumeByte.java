/*
 * Copyright (c) 2026 Weasis Team and other contributors.
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

public final class VolumeByte extends Volume<Byte, byte[]> {

  public VolumeByte(
      int sizeX, int sizeY, int sizeZ, boolean signed, int channels, JProgressBar progressBar) {
    super(
        sizeX,
        sizeY,
        sizeZ,
        signed ? CvType.CV_8SC(channels) : CvType.CV_8UC(channels),
        progressBar);
  }

  public VolumeByte(OriginalStack stack, JProgressBar progressBar) {
    super(stack, progressBar);
  }

  public VolumeByte(
      Volume<Byte, byte[]> volume, int sizeX, int sizeY, int sizeZ, Vector3d voxelRatio) {
    super(volume, sizeX, sizeY, sizeZ, voxelRatio);
  }

  @Override
  protected Byte initMinValue() {
    return isSigned ? Byte.MIN_VALUE : 0;
  }

  @Override
  protected Byte initMaxValue() {
    return isSigned ? Byte.MAX_VALUE : (byte) 0xFF;
  }

  @Override
  protected int initCVType(boolean isSigned, int channels) {
    return isSigned ? CvType.CV_8SC(channels) : CvType.CV_8UC(channels);
  }

  @Override
  protected ChunkedArray<byte[]> createChunkedArray(long totalElements) {
    return ChunkedArray.ofByte(totalElements);
  }

  @Override
  protected void setElementInData(long index, Byte value) {
    data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)] = value;
  }

  @Override
  protected Byte getElementFromData(long index) {
    return data.getChunk(data.chunkIndex(index))[data.chunkOffset(index)];
  }

  @Override
  protected void copyFrom(PlanarImage image, int sliceIndex, Matrix4d transform, Dimension dim) {
    int pixelCount = dim.width * dim.height;
    byte[] pixelData = new byte[pixelCount * channels];
    image.get(0, 0, pixelData);

    if (isIdentityTransform(transform)) {
      long destOffset = (long) sliceIndex * size.y * size.x * channels;
      if (data != null) {
        data.copyFrom(destOffset, pixelData, 0, pixelData.length);
      } else {
        long byteOffset = destOffset * byteDepth;
        for (int i = 0; i < pixelData.length; i++) {
          mappedBuffer.put(byteOffset + i, pixelData[i]);
        }
      }
    } else {
      // Slow path: per-pixel coordinate remapping
      copyPixels(
          dim,
          (x, y) -> {
            int pixelIndex = (y * dim.width + x) * channels;
            Vector3i coord = mapSliceToVolumeCoordinates(x, y, sliceIndex, transform);
            if (isOutside(coord.x, coord.y, coord.z)) {
              return;
            }
            long baseIndex =
                ((long) coord.z * size.y * size.x + (long) coord.y * size.x + coord.x) * channels;
            if (data != null) {
              for (int c = 0; c < channels; c++) {
                long idx = baseIndex + c;
                setElementInData(idx, pixelData[pixelIndex + c]);
              }
            } else {
              for (int c = 0; c < channels; c++) {
                mappedBuffer.put((baseIndex + c) * byteDepth, pixelData[pixelIndex + c]);
              }
            }
          });
    }
  }

  @Override
  protected void setChannelValues(long baseIndex, Voxel<Byte> voxel) {
    if (data != null) {
      for (int c = 0; c < channels; c++) {
        Byte val = voxel.getValue(c);
        if (val != null) {
          long idx = baseIndex + c;
          data.getChunk(data.chunkIndex(idx))[data.chunkOffset(idx)] = val;
        }
      }
    } else {
      for (int c = 0; c < channels; c++) {
        Byte val = voxel.getValue(c);
        if (val != null) {
          mappedBuffer.put((baseIndex + c) * byteDepth, val);
        }
      }
    }
  }

  @Override
  public void writeVolume(DataOutputStream dos, int x, int y, int z) throws IOException {
    for (int c = 0; c < channels; c++) {
      Byte channelValue = getValue(x, y, z, c);
      if (channelValue == null) {
        throw new IOException(
            "Null voxel channel value at (" + x + "," + y + "," + z + "), channel " + c);
      }
      dos.writeByte(channelValue);
    }
  }

  @Override
  public void readVolume(DataInputStream dis, int x, int y, int z) throws IOException {
    for (int c = 0; c < channels; c++) {
      Byte channelValue = dis.readByte();
      setChannelValue(x, y, z, c, channelValue);
    }
  }
}
