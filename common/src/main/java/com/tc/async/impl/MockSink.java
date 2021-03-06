/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */

package com.tc.async.impl;

import com.tc.async.api.Sink;
import com.tc.async.api.SpecializedEventContext;
import com.tc.stats.Stats;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author orion
 */
public class MockSink<EC> implements Sink<EC> {

  private volatile boolean closed = false;
  public BlockingQueue<EC> queue = new LinkedBlockingQueue<EC>(); // its not bounded

  public EC take() {
    try {
      return this.queue.take();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void setClosed(boolean closed) {
    this.closed = closed;
  }

  @Override
  public void addSingleThreaded(EC context) {
    if (closed) {
      throw new IllegalStateException("closed");
    }
    try {
      this.queue.put(context);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @Override
  public void addMultiThreaded(EC context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addSpecialized(SpecializedEventContext specialized) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int size() {
    return this.queue.size();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void enableStatsCollection(boolean enable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Stats getStats(long frequency) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Stats getStatsAndReset(long frequency) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isStatsCollectionEnabled() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void resetStats() {
    throw new UnsupportedOperationException();
  }
}
