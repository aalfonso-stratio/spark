/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved
 *
 * This software is a modification of the original software Apache Spark licensed under the Apache 2.0
 * license, a copy of which is below. This software contains proprietary information of
 * Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or
 * otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled,
 * without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package org.apache.spark.storage;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.spark.annotation.Private;
import org.apache.spark.executor.ShuffleWriteMetrics;

/**
 * Intercepts write calls and tracks total time spent writing in order to update shuffle write
 * metrics. Not thread safe.
 */
@Private
public final class TimeTrackingOutputStream extends OutputStream {

  private final ShuffleWriteMetrics writeMetrics;
  private final OutputStream outputStream;

  public TimeTrackingOutputStream(ShuffleWriteMetrics writeMetrics, OutputStream outputStream) {
    this.writeMetrics = writeMetrics;
    this.outputStream = outputStream;
  }

  @Override
  public void write(int b) throws IOException {
    final long startTime = System.nanoTime();
    outputStream.write(b);
    writeMetrics.incWriteTime(System.nanoTime() - startTime);
  }

  @Override
  public void write(byte[] b) throws IOException {
    final long startTime = System.nanoTime();
    outputStream.write(b);
    writeMetrics.incWriteTime(System.nanoTime() - startTime);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    final long startTime = System.nanoTime();
    outputStream.write(b, off, len);
    writeMetrics.incWriteTime(System.nanoTime() - startTime);
  }

  @Override
  public void flush() throws IOException {
    final long startTime = System.nanoTime();
    outputStream.flush();
    writeMetrics.incWriteTime(System.nanoTime() - startTime);
  }

  @Override
  public void close() throws IOException {
    final long startTime = System.nanoTime();
    outputStream.close();
    writeMetrics.incWriteTime(System.nanoTime() - startTime);
  }
}
