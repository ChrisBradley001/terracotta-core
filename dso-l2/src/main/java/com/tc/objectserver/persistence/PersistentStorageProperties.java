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
package com.tc.objectserver.persistence;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

import com.tc.classloader.CommonComponent;

/**
 *
 */
@CommonComponent
public class PersistentStorageProperties extends ConcurrentHashMap<String, String> implements Serializable {
  
  private transient FlushingWrite write;

  public PersistentStorageProperties(FlushingWrite write) {
    this.write = write;
  }
  
  public void setWriter(FlushingWrite write) {
    this.write = write;
  }

  @Override
  public synchronized String put(String key, String value) {
    return write.run(()->PersistentStorageProperties.super.put(key, value));
  }
  
}
