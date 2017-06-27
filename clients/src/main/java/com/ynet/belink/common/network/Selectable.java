/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ynet.belink.common.network;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * An interface for asynchronous, multi-channel network I/O
 */
public interface Selectable {

    /**
     * See {@link #connect(String, InetSocketAddress, int, int) connect()}
     */
    public static final int USE_DEFAULT_BUFFER_SIZE = -1;

    /**
     * Begin establishing a socket connection to the given address identified by the given address
     * @param id The id for this connection
     * @param address The address to connect to
     * @param sendBufferSize The send buffer for the socket
     * @param receiveBufferSize The receive buffer for the socket
     * @throws IOException If we cannot begin connecting
     */
    public void connect(String id, InetSocketAddress address, int sendBufferSize, int receiveBufferSize) throws IOException;

    /**
     * Wakeup this selector if it is blocked on I/O
     */
    public void wakeup();

    /**
     * Close this selector
     */
    public void close();

    /**
     * Close the connection identified by the given id
     */
    public void close(String id);

    /**
     * Queue the given request for sending in the subsequent {@link #poll(long) poll()} calls
     * @param send The request to send
     */
    public void send(Send send);

    /**
     * Do I/O. Reads, writes, connection establishment, etc.
     * @param timeout The amount of time to block if there is nothing to do
     * @throws IOException
     */
    public void poll(long timeout) throws IOException;

    /**
     * The list of sends that completed on the last {@link #poll(long) poll()} call.
     */
    public List<Send> completedSends();

    /**
     * The list of receives that completed on the last {@link #poll(long) poll()} call.
     */
    public List<NetworkReceive> completedReceives();

    /**
     * The list of connections that finished disconnecting on the last {@link #poll(long) poll()}
     * call.
     */
    public List<String> disconnected();

    /**
     * The list of connections that completed their connection on the last {@link #poll(long) poll()}
     * call.
     */
    public List<String> connected();

    /**
     * Disable reads from the given connection
     * @param id The id for the connection
     */
    public void mute(String id);

    /**
     * Re-enable reads from the given connection
     * @param id The id for the connection
     */
    public void unmute(String id);

    /**
     * Disable reads from all connections
     */
    public void muteAll();

    /**
     * Re-enable reads from all connections
     */
    public void unmuteAll();

    /**
     * returns true  if a channel is ready
     * @param id The id for the connection
     */
    public boolean isChannelReady(String id);
}
