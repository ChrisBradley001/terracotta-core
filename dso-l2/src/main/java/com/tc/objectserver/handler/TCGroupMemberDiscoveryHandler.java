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
package com.tc.objectserver.handler;

import com.tc.async.api.AbstractEventHandler;
import com.tc.async.api.ConfigurationContext;
import com.tc.net.groups.DiscoveryStateMachine;
import com.tc.net.groups.TCGroupManagerImpl;

public class TCGroupMemberDiscoveryHandler extends AbstractEventHandler<DiscoveryStateMachine> {
  private final TCGroupManagerImpl manager;
  
  public TCGroupMemberDiscoveryHandler(TCGroupManagerImpl manager) {
    this.manager = manager;
  }
  
  @Override
  public void handleEvent(DiscoveryStateMachine context) {
    manager.getDiscover().discoveryHandler(context);
  }

  @Override
  public void initialize(ConfigurationContext context) {
    super.initialize(context);
  }

}