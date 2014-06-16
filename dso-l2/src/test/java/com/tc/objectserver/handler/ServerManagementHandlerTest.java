package com.tc.objectserver.handler;

import org.junit.Test;

import com.tc.management.ManagementEventListener;
import com.tc.management.TCManagementEvent;
import com.tc.net.ClientID;
import com.tc.net.TCSocketAddress;
import com.tc.net.protocol.tcm.MessageChannel;
import com.tc.object.management.ResponseHolder;
import com.tc.object.msg.InvokeRegisteredServiceResponseMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Ludovic Orban
 */
public class ServerManagementHandlerTest {

  @Test
  public void testArrivingL1EventsAreForwardedToListeners() throws Exception {
    final AtomicBoolean listenerCalled = new AtomicBoolean(false);
    InvokeRegisteredServiceResponseMessage event = mock(InvokeRegisteredServiceResponseMessage.class);
    when(event.getSourceNodeID()).thenReturn(new ClientID(123L));
    MessageChannel channel = mock(MessageChannel.class);
    when(event.getChannel()).thenReturn(channel);
    when(channel.getRemoteAddress()).thenReturn(new TCSocketAddress(456));
    final TCManagementEvent tcManagementEvent = new TCManagementEvent("this is my test response", "test.type");
    ResponseHolder responseHolder = new ResponseHolder(tcManagementEvent);
    when(event.getResponseHolder()).thenReturn(responseHolder);

    ServerManagementHandler serverManagementHandler = new ServerManagementHandler();
    serverManagementHandler.registerEventListener(new ManagementEventListener() {
      @Override
      public ClassLoader getClassLoader() {
        return ServerManagementHandlerTest.class.getClassLoader();
      }

      @Override
      public void onEvent(TCManagementEvent event, Map<String, Object> context) {
        listenerCalled.set(true);
        assertThat(event.getType(), equalTo(tcManagementEvent.getType()));
        assertThat(event.getPayload(), equalTo(tcManagementEvent.getPayload()));
        assertThat((String)context.get(ManagementEventListener.CONTEXT_SOURCE_NODE_NAME), equalTo("123"));
        assertThat((String)context.get(ManagementEventListener.CONTEXT_SOURCE_JMX_ID), endsWith("_456"));
      }
    });

    serverManagementHandler.handleEvent(event);
    assertThat("Expected listener to be called", listenerCalled.get(), is(true));
  }

  @Test
  public void testFireEventCallsListeners() throws Exception {
    final AtomicBoolean listenerCalled = new AtomicBoolean(false);
    final TCManagementEvent tcManagementEvent = new TCManagementEvent("this is my test response", "test.type");
    final Map<String, Object> context = new HashMap<String, Object>();

    ServerManagementHandler serverManagementHandler = new ServerManagementHandler();
    serverManagementHandler.registerEventListener(new ManagementEventListener() {
      @Override
      public ClassLoader getClassLoader() {
        return ServerManagementHandlerTest.class.getClassLoader();
      }

      @Override
      public void onEvent(TCManagementEvent event, Map<String, Object> ctx) {
        listenerCalled.set(true);
        assertThat(event.getType(), equalTo(tcManagementEvent.getType()));
        assertThat(event.getPayload(), equalTo(tcManagementEvent.getPayload()));
        assertThat(ctx, equalTo(context));
      }
    });

    serverManagementHandler.fireEvent(tcManagementEvent, context);
    assertThat("Expected listener to be called", listenerCalled.get(), is(true));
  }
}
