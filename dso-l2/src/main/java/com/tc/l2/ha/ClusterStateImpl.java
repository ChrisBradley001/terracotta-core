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
package com.tc.l2.ha;

import com.tc.logging.TCLogger;
import com.tc.logging.TCLogging;
import com.tc.net.GroupID;
import com.tc.net.StripeID;
import com.tc.net.groups.StripeIDStateManager;
import com.tc.net.protocol.transport.ConnectionID;
import com.tc.net.protocol.transport.ConnectionIDFactory;
import com.tc.objectserver.persistence.ClusterStatePersistor;
import com.tc.util.Assert;
import com.tc.util.State;
import com.tc.util.UUID;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ClusterStateImpl implements ClusterState {

  private static final TCLogger                     logger                 = TCLogging.getLogger(ClusterState.class);

  private final ClusterStatePersistor               clusterStatePersistor;
  private final ConnectionIDFactory                 connectionIdFactory;
  private final GroupID                             thisGroupID;
  private final StripeIDStateManager                stripeIDStateManager;

  private final Set<ConnectionID>                   connections            = Collections.synchronizedSet(new HashSet<ConnectionID>());
  private long                                      nextAvailChannelID     = -1;
  private State                                     currentState;
  private StripeID                                  stripeID;

  public ClusterStateImpl(ClusterStatePersistor clusterStatePersistor,
                          ConnectionIDFactory connectionIdFactory,
                          GroupID thisGroupID, StripeIDStateManager stripeIDStateManager) {
    this.clusterStatePersistor = clusterStatePersistor;
    this.connectionIdFactory = connectionIdFactory;
    this.thisGroupID = thisGroupID;
    this.stripeIDStateManager = stripeIDStateManager;
    this.stripeID = clusterStatePersistor.getThisStripeID();
    this.nextAvailChannelID = this.connectionIdFactory.getCurrentConnectionID();
    checkAndSetGroupID(clusterStatePersistor, thisGroupID);
  }

  private void checkAndSetGroupID(ClusterStatePersistor statePersistor, GroupID groupID) {
    if (statePersistor.getGroupId().isNull()) {
      statePersistor.setGroupId(thisGroupID);
    } else if (!groupID.equals(statePersistor.getGroupId())) {
      logger.error("Found data from the incorrect stripe in the server data path. Verify that the server is starting up " +
                   "with the correct data files and that the cluster topology has not changed across a restart.");
      throw new IllegalStateException("Data for " + statePersistor.getGroupId() + " found. Expected data from group " + thisGroupID + ".");
    }
  }

  @Override
  public long getNextAvailableChannelID() {
    return nextAvailChannelID;
  }

  @Override
  public void setNextAvailableChannelID(long nextAvailableCID) {
    if (nextAvailableCID < nextAvailChannelID) {
      // Could happen when two actives fight it out. Dont want to assert, let the state manager fight it out.
      logger.error("Trying to set Next Available ChannelID to a lesser value : known = " + nextAvailChannelID
                   + " new value = " + nextAvailableCID + " IGNORING");
      return;
    }
    this.nextAvailChannelID = nextAvailableCID;
  }

  @Override
  public void syncActiveState() {
    syncConnectionIDsToDisk();
  }

  @Override
  public void syncSequenceState() {
  }

  private void syncConnectionIDsToDisk() {
    Assert.assertNotNull(stripeID);
    connectionIdFactory.init(stripeID.getName(), nextAvailChannelID, connections);
  }

  @Override
  public StripeID getStripeID() {
    return stripeID;
  }

  public boolean isStripeIDNull() {
    return stripeID.isNull();
  }

  @Override
  public void setStripeID(String uid) {
    if (!isStripeIDNull() && !stripeID.getName().equals(uid)) {
      logger.error("StripeID doesnt match !! Mine : " + stripeID + " Active sent clusterID as : " + uid);
      throw new ClusterIDMissmatchException(stripeID.getName(), uid);
    }
    stripeID = new StripeID(uid);
    syncStripeIDToDB();

    // notify stripeIDStateManager
    stripeIDStateManager.verifyOrSaveStripeID(thisGroupID, stripeID, true);
  }

  private void syncStripeIDToDB() {
    clusterStatePersistor.setThisStripeID(stripeID);
  }

  @Override
  public void setCurrentState(State state) {
    this.currentState = state;
    syncCurrentStateToDB();
  }

  private void syncCurrentStateToDB() {
    clusterStatePersistor.setCurrentL2State(currentState);
  }

  @Override
  public void addNewConnection(ConnectionID connID) {
    if (connID.getChannelID() >= nextAvailChannelID) {
      nextAvailChannelID = connID.getChannelID() + 1;
    }
    connections.add(connID);
  }

  @Override
  public void removeConnection(ConnectionID connectionID) {
    boolean removed = connections.remove(connectionID);
    if (!removed) {
      logger.warn("Connection ID not found : " + connectionID + " Current Connections count : " + connections.size());
    }
  }

  @Override
  public Set<ConnectionID> getAllConnections() {
    return new HashSet<>(connections);
  }

  @Override
  public void generateStripeIDIfNeeded() {
    if (isStripeIDNull()) {
      // This is the first time an L2 goes active in the cluster of L2s. Generate a new stripeID. this will stick.
      setStripeID(UUID.getUUID().toString());
    }
  }

  @Override
  public Map<GroupID, StripeID> getStripeIDMap() {
    return stripeIDStateManager.getStripeIDMap(false);
  }

  @Override
  public void addToStripeIDMap(GroupID gid, StripeID sid) {
    stripeIDStateManager.verifyOrSaveStripeID(gid, sid, true);
  }

  @Override
  public String toString() {
    StringBuilder strBuilder = new StringBuilder();
    strBuilder.append("ClusterState [ ");
    strBuilder.append("Connections [ ").append(this.connections).append(" ]");
    strBuilder.append(" nextAvailChannelID: ").append(this.nextAvailChannelID);
    strBuilder.append(" currentState: ").append(this.currentState);
    strBuilder.append(" stripeID: ").append(this.stripeID);
    strBuilder.append(" ]");
    return strBuilder.toString();
  }

}
