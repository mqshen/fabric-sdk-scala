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

import com.ynet.belink.common.BelinkException;
import com.ynet.belink.common.MetricName;
import com.ynet.belink.common.metrics.Measurable;
import com.ynet.belink.common.metrics.MetricConfig;
import com.ynet.belink.common.metrics.Metrics;
import com.ynet.belink.common.metrics.Sensor;
import com.ynet.belink.common.metrics.stats.Avg;
import com.ynet.belink.common.metrics.stats.Count;
import com.ynet.belink.common.metrics.stats.Max;
import com.ynet.belink.common.metrics.stats.Rate;
import com.ynet.belink.common.utils.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * A nioSelector interface for doing non-blocking multi-connection network I/O.
 * <p>
 * This class works with {@link NetworkSend} and {@link NetworkReceive} to transmit size-delimited network requests and
 * responses.
 * <p>
 * A connection can be added to the nioSelector associated with an integer id by doing
 *
 * <pre>
 * nioSelector.connect(&quot;42&quot;, new InetSocketAddress(&quot;google.com&quot;, server.port), 64000, 64000);
 * </pre>
 *
 * The connect call does not block on the creation of the TCP connection, so the connect method only begins initiating
 * the connection. The successful invocation of this method does not mean a valid connection has been established.
 *
 * Sending requests, receiving responses, processing connection completions, and disconnections on the existing
 * connections are all done using the <code>poll()</code> call.
 *
 * <pre>
 * nioSelector.send(new NetworkSend(myDestination, myBytes));
 * nioSelector.send(new NetworkSend(myOtherDestination, myOtherBytes));
 * nioSelector.poll(TIMEOUT_MS);
 * </pre>
 *
 * The nioSelector maintains several lists that are reset by each call to <code>poll()</code> which are available via
 * various getters. These are reset by each call to <code>poll()</code>.
 *
 * This class is not thread safe!
 */
public class Selector implements Selectable {

    public static final long NO_IDLE_TIMEOUT_MS = -1;
    private static final Logger log = LoggerFactory.getLogger(Selector.class);

    private final java.nio.channels.Selector nioSelector;
    private final Map<String, BelinkChannel> channels;
    private final List<Send> completedSends;
    private final List<NetworkReceive> completedReceives;
    private final Map<BelinkChannel, Deque<NetworkReceive>> stagedReceives;
    private final Set<SelectionKey> immediatelyConnectedKeys;
    private final Map<String, BelinkChannel> closingChannels;
    private final List<String> disconnected;
    private final List<String> connected;
    private final List<String> failedSends;
    private final Time time;
    private final SelectorMetrics sensors;
    private final String metricGrpPrefix;
    private final Map<String, String> metricTags;
    private final ChannelBuilder channelBuilder;
    private final int maxReceiveSize;
    private final boolean metricsPerConnection;
    private final IdleExpiryManager idleExpiryManager;

    /**
     * Create a new nioSelector
     *
     * @param maxReceiveSize Max size in bytes of a single network receive (use {@link NetworkReceive#UNLIMITED} for no limit)
     * @param connectionMaxIdleMs Max idle connection time (use {@link #NO_IDLE_TIMEOUT_MS} to disable idle timeout)
     * @param metrics Registry for Selector metrics
     * @param time Time implementation
     * @param metricGrpPrefix Prefix for the group of metrics registered by Selector
     * @param metricTags Additional tags to add to metrics registered by Selector
     * @param metricsPerConnection Whether or not to enable per-connection metrics
     * @param channelBuilder Channel builder for every new connection
     */
    public Selector(int maxReceiveSize,
                    long connectionMaxIdleMs,
                    Metrics metrics,
                    Time time,
                    String metricGrpPrefix,
                    Map<String, String> metricTags,
                    boolean metricsPerConnection,
                    ChannelBuilder channelBuilder) {
        try {
            this.nioSelector = java.nio.channels.Selector.open();
        } catch (IOException e) {
            throw new BelinkException(e);
        }
        this.maxReceiveSize = maxReceiveSize;
        this.time = time;
        this.metricGrpPrefix = metricGrpPrefix;
        this.metricTags = metricTags;
        this.channels = new HashMap<>();
        this.completedSends = new ArrayList<>();
        this.completedReceives = new ArrayList<>();
        this.stagedReceives = new HashMap<>();
        this.immediatelyConnectedKeys = new HashSet<>();
        this.closingChannels = new HashMap<>();
        this.connected = new ArrayList<>();
        this.disconnected = new ArrayList<>();
        this.failedSends = new ArrayList<>();
        this.sensors = new SelectorMetrics(metrics);
        this.channelBuilder = channelBuilder;
        this.metricsPerConnection = metricsPerConnection;
        this.idleExpiryManager = connectionMaxIdleMs < 0 ? null : new IdleExpiryManager(time, connectionMaxIdleMs);
    }

    public Selector(long connectionMaxIdleMS, Metrics metrics, Time time, String metricGrpPrefix, ChannelBuilder channelBuilder) {
        this(NetworkReceive.UNLIMITED, connectionMaxIdleMS, metrics, time, metricGrpPrefix, new HashMap<String, String>(), true, channelBuilder);
    }

    /**
     * Begin connecting to the given address and add the connection to this nioSelector associated with the given id
     * number.
     * <p>
     * Note that this call only initiates the connection, which will be completed on a future {@link #poll(long)}
     * call. Check {@link #connected()} to see which (if any) connections have completed after a given poll call.
     * @param id The id for the new connection
     * @param address The address to connect to
     * @param sendBufferSize The send buffer for the new connection
     * @param receiveBufferSize The receive buffer for the new connection
     * @throws IllegalStateException if there is already a connection for that id
     * @throws IOException if DNS resolution fails on the hostname or if the broker is down
     */
    @Override
    public void connect(String id, InetSocketAddress address, int sendBufferSize, int receiveBufferSize) throws IOException {
        if (this.channels.containsKey(id))
            throw new IllegalStateException("There is already a connection for id " + id);

        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        Socket socket = socketChannel.socket();
        socket.setKeepAlive(true);
        if (sendBufferSize != Selectable.USE_DEFAULT_BUFFER_SIZE)
            socket.setSendBufferSize(sendBufferSize);
        if (receiveBufferSize != Selectable.USE_DEFAULT_BUFFER_SIZE)
            socket.setReceiveBufferSize(receiveBufferSize);
        socket.setTcpNoDelay(true);
        boolean connected;
        try {
            connected = socketChannel.connect(address);
        } catch (UnresolvedAddressException e) {
            socketChannel.close();
            throw new IOException("Can't resolve address: " + address, e);
        } catch (IOException e) {
            socketChannel.close();
            throw e;
        }
        SelectionKey key = socketChannel.register(nioSelector, SelectionKey.OP_CONNECT);
        BelinkChannel channel = channelBuilder.buildChannel(id, key, maxReceiveSize);
        key.attach(channel);
        this.channels.put(id, channel);

        if (connected) {
            // OP_CONNECT won't trigger for immediately connected channels
            log.debug("Immediately connected to node {}", channel.id());
            immediatelyConnectedKeys.add(key);
            key.interestOps(0);
        }
    }

    /**
     * Register the nioSelector with an existing channel
     * Use this on server-side, when a connection is accepted by a different thread but processed by the Selector
     * Note that we are not checking if the connection id is valid - since the connection already exists
     */
    public void register(String id, SocketChannel socketChannel) throws ClosedChannelException {
        SelectionKey key = socketChannel.register(nioSelector, SelectionKey.OP_READ);
        BelinkChannel channel = channelBuilder.buildChannel(id, key, maxReceiveSize);
        key.attach(channel);
        this.channels.put(id, channel);
    }

    /**
     * Interrupt the nioSelector if it is blocked waiting to do I/O.
     */
    @Override
    public void wakeup() {
        this.nioSelector.wakeup();
    }

    /**
     * Close this selector and all associated connections
     */
    @Override
    public void close() {
        List<String> connections = new ArrayList<>(channels.keySet());
        for (String id : connections)
            close(id);
        try {
            this.nioSelector.close();
        } catch (IOException | SecurityException e) {
            log.error("Exception closing nioSelector:", e);
        }
        sensors.close();
        channelBuilder.close();
    }

    /**
     * Queue the given request for sending in the subsequent {@link #poll(long)} calls
     * @param send The request to send
     */
    public void send(Send send) {
        String connectionId = send.destination();
        if (closingChannels.containsKey(connectionId))
            this.failedSends.add(connectionId);
        else {
            BelinkChannel channel = channelOrFail(connectionId, false);
            try {
                channel.setSend(send);
            } catch (CancelledKeyException e) {
                this.failedSends.add(connectionId);
                close(channel, false);
            }
        }
    }

    /**
     * Do whatever I/O can be done on each connection without blocking. This includes completing connections, completing
     * disconnections, initiating new sends, or making progress on in-progress sends or receives.
     *
     * When this call is completed the user can check for completed sends, receives, connections or disconnects using
     * {@link #completedSends()}, {@link #completedReceives()}, {@link #connected()}, {@link #disconnected()}. These
     * lists will be cleared at the beginning of each `poll` call and repopulated by the call if there is
     * any completed I/O.
     *
     * In the "Plaintext" setting, we are using socketChannel to read & write to the network. But for the "SSL" setting,
     * we encrypt the data before we use socketChannel to write data to the network, and decrypt before we return the responses.
     * This requires additional buffers to be maintained as we are reading from network, since the data on the wire is encrypted
     * we won't be able to read exact no.of bytes as kafka protocol requires. We read as many bytes as we can, up to SSLEngine's
     * application buffer size. This means we might be reading additional bytes than the requested size.
     * If there is no further data to read from socketChannel selector won't invoke that channel and we've have additional bytes
     * in the buffer. To overcome this issue we added "stagedReceives" map which contains per-channel deque. When we are
     * reading a channel we read as many responses as we can and store them into "stagedReceives" and pop one response during
     * the poll to add the completedReceives. If there are any active channels in the "stagedReceives" we set "timeout" to 0
     * and pop response and add to the completedReceives.
     *
     * Atmost one entry is added to "completedReceives" for a channel in each poll. This is necessary to guarantee that
     * requests from a channel are processed on the broker in the order they are sent. Since outstanding requests added
     * by SocketServer to the request queue may be processed by different request handler threads, requests on each
     * channel must be processed one-at-a-time to guarantee ordering.
     *
     * @param timeout The amount of time to wait, in milliseconds, which must be non-negative
     * @throws IllegalArgumentException If `timeout` is negative
     * @throws IllegalStateException If a send is given for which we have no existing connection or for which there is
     *         already an in-progress send
     */
    @Override
    public void poll(long timeout) throws IOException {
        if (timeout < 0)
            throw new IllegalArgumentException("timeout should be >= 0");

        clear();

        if (hasStagedReceives() || !immediatelyConnectedKeys.isEmpty())
            timeout = 0;

        /* check ready keys */
        long startSelect = time.nanoseconds();
        int readyKeys = select(timeout);
        long endSelect = time.nanoseconds();
        this.sensors.selectTime.record(endSelect - startSelect, time.milliseconds());

        if (readyKeys > 0 || !immediatelyConnectedKeys.isEmpty()) {
            pollSelectionKeys(this.nioSelector.selectedKeys(), false, endSelect);
            pollSelectionKeys(immediatelyConnectedKeys, true, endSelect);
        }

        addToCompletedReceives();

        long endIo = time.nanoseconds();
        this.sensors.ioTime.record(endIo - endSelect, time.milliseconds());

        // we use the time at the end of select to ensure that we don't close any connections that
        // have just been processed in pollSelectionKeys
        maybeCloseOldestConnection(endSelect);
    }

    private void pollSelectionKeys(Iterable<SelectionKey> selectionKeys,
                                   boolean isImmediatelyConnected,
                                   long currentTimeNanos) {
        Iterator<SelectionKey> iterator = selectionKeys.iterator();
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();
            BelinkChannel channel = channel(key);

            // register all per-connection metrics at once
            sensors.maybeRegisterConnectionMetrics(channel.id());
            if (idleExpiryManager != null)
                idleExpiryManager.update(channel.id(), currentTimeNanos);

            try {

                /* complete any connections that have finished their handshake (either normally or immediately) */
                if (isImmediatelyConnected || key.isConnectable()) {
                    if (channel.finishConnect()) {
                        this.connected.add(channel.id());
                        this.sensors.connectionCreated.record();
                        SocketChannel socketChannel = (SocketChannel) key.channel();
                        log.debug("Created socket with SO_RCVBUF = {}, SO_SNDBUF = {}, SO_TIMEOUT = {} to node {}",
                                socketChannel.socket().getReceiveBufferSize(),
                                socketChannel.socket().getSendBufferSize(),
                                socketChannel.socket().getSoTimeout(),
                                channel.id());
                    } else
                        continue;
                }

                /* if channel is not ready finish prepare */
                if (channel.isConnected() && !channel.ready())
                    channel.prepare();

                /* if channel is ready read from any connections that have readable data */
                if (channel.ready() && key.isReadable() && !hasStagedReceive(channel)) {
                    NetworkReceive networkReceive;
                    while ((networkReceive = channel.read()) != null)
                        addToStagedReceives(channel, networkReceive);
                }

                /* if channel is ready write to any sockets that have space in their buffer and for which we have data */
                if (channel.ready() && key.isWritable()) {
                    Send send = channel.write();
                    if (send != null) {
                        this.completedSends.add(send);
                        this.sensors.recordBytesSent(channel.id(), send.size());
                    }
                }

                /* cancel any defunct sockets */
                if (!key.isValid())
                    close(channel, true);

            } catch (Exception e) {
                String desc = channel.socketDescription();
                if (e instanceof IOException)
                    log.debug("Connection with {} disconnected", desc, e);
                else
                    log.warn("Unexpected error from {}; closing connection", desc, e);
                close(channel, true);
            }
        }
    }

    @Override
    public List<Send> completedSends() {
        return this.completedSends;
    }

    @Override
    public List<NetworkReceive> completedReceives() {
        return this.completedReceives;
    }

    @Override
    public List<String> disconnected() {
        return this.disconnected;
    }

    @Override
    public List<String> connected() {
        return this.connected;
    }

    @Override
    public void mute(String id) {
        BelinkChannel channel = channelOrFail(id, true);
        mute(channel);
    }

    private void mute(BelinkChannel channel) {
        channel.mute();
    }

    @Override
    public void unmute(String id) {
        BelinkChannel channel = channelOrFail(id, true);
        unmute(channel);
    }

    private void unmute(BelinkChannel channel) {
        channel.unmute();
    }

    @Override
    public void muteAll() {
        for (BelinkChannel channel : this.channels.values())
            mute(channel);
    }

    @Override
    public void unmuteAll() {
        for (BelinkChannel channel : this.channels.values())
            unmute(channel);
    }

    private void maybeCloseOldestConnection(long currentTimeNanos) {
        if (idleExpiryManager == null)
            return;

        Map.Entry<String, Long> expiredConnection = idleExpiryManager.pollExpiredConnection(currentTimeNanos);
        if (expiredConnection != null) {
            String connectionId = expiredConnection.getKey();
            BelinkChannel channel = this.channels.get(connectionId);
            if (channel != null) {
                if (log.isTraceEnabled())
                    log.trace("About to close the idle connection from {} due to being idle for {} millis",
                            connectionId, (currentTimeNanos - expiredConnection.getValue()) / 1000 / 1000);
                close(channel, true);
            }
        }
    }

    /**
     * Clear the results from the prior poll
     */
    private void clear() {
        this.completedSends.clear();
        this.completedReceives.clear();
        this.connected.clear();
        this.disconnected.clear();
        // Remove closed channels after all their staged receives have been processed or if a send was requested
        for (Iterator<Map.Entry<String, BelinkChannel>> it = closingChannels.entrySet().iterator(); it.hasNext(); ) {
            BelinkChannel channel = it.next().getValue();
            Deque<NetworkReceive> deque = this.stagedReceives.get(channel);
            boolean sendFailed = failedSends.remove(channel.id());
            if (deque == null || deque.isEmpty() || sendFailed) {
                doClose(channel, true);
                it.remove();
            }
        }
        this.disconnected.addAll(this.failedSends);
        this.failedSends.clear();
    }

    /**
     * Check for data, waiting up to the given timeout.
     *
     * @param ms Length of time to wait, in milliseconds, which must be non-negative
     * @return The number of keys ready
     * @throws IllegalArgumentException
     * @throws IOException
     */
    private int select(long ms) throws IOException {
        if (ms < 0L)
            throw new IllegalArgumentException("timeout should be >= 0");

        if (ms == 0L)
            return this.nioSelector.selectNow();
        else
            return this.nioSelector.select(ms);
    }

    /**
     * Close the connection identified by the given id
     */
    public void close(String id) {
        BelinkChannel channel = this.channels.get(id);
        if (channel != null)
            close(channel, false);
    }

    /**
     * Begin closing this connection.
     *
     * If 'processOutstanding' is true, the channel is disconnected here, but staged receives are
     * processed. The channel is closed when there are no outstanding receives or if a send
     * is requested. The channel will be added to disconnect list when it is actually closed.
     *
     * If 'processOutstanding' is false, outstanding receives are discarded and the channel is
     * closed immediately. The channel will not be added to disconnected list and it is the
     * responsibility of the caller to handle disconnect notifications.
     */
    private void close(BelinkChannel channel, boolean processOutstanding) {

        channel.disconnect();

        // Keep track of closed channels with pending receives so that all received records
        // may be processed. For example, when producer with acks=0 sends some records and
        // closes its connections, a single poll() in the broker may receive records and
        // handle close(). When the remote end closes its connection, the channel is retained until
        // a send fails or all outstanding receives are processed. Mute state of disconnected channels
        // are tracked to ensure that requests are processed one-by-one by the broker to preserve ordering.
        Deque<NetworkReceive> deque = this.stagedReceives.get(channel);
        if (processOutstanding && deque != null && !deque.isEmpty()) {
            if (!channel.isMute()) {
                addToCompletedReceives(channel, deque);
                if (deque.isEmpty())
                    this.stagedReceives.remove(channel);
            }
            closingChannels.put(channel.id(), channel);
        } else
            doClose(channel, processOutstanding);
        this.channels.remove(channel.id());

        if (idleExpiryManager != null)
            idleExpiryManager.remove(channel.id());
    }

    private void doClose(BelinkChannel channel, boolean notifyDisconnect) {
        try {
            channel.close();
        } catch (IOException e) {
            log.error("Exception closing connection to node {}:", channel.id(), e);
        }
        this.sensors.connectionClosed.record();
        this.stagedReceives.remove(channel);
        if (notifyDisconnect)
            this.disconnected.add(channel.id());
    }

    /**
     * check if channel is ready
     */
    @Override
    public boolean isChannelReady(String id) {
        BelinkChannel channel = this.channels.get(id);
        return channel != null && channel.ready();
    }

    private BelinkChannel channelOrFail(String id, boolean maybeClosing) {
        BelinkChannel channel = this.channels.get(id);
        if (channel == null && maybeClosing)
            channel = this.closingChannels.get(id);
        if (channel == null)
            throw new IllegalStateException("Attempt to retrieve channel for which there is no connection. Connection id " + id + " existing connections " + channels.keySet());
        return channel;
    }

    /**
     * Return the selector channels.
     */
    public List<BelinkChannel> channels() {
        return new ArrayList<>(channels.values());
    }

    /**
     * Return the channel associated with this connection or `null` if there is no channel associated with the
     * connection.
     */
    public BelinkChannel channel(String id) {
        return this.channels.get(id);
    }

    /**
     * Return the channel with the specified id if it was disconnected, but not yet closed
     * since there are outstanding messages to be processed.
     */
    public BelinkChannel closingChannel(String id) {
        return closingChannels.get(id);
    }

    /**
     * Get the channel associated with selectionKey
     */
    private BelinkChannel channel(SelectionKey key) {
        return (BelinkChannel) key.attachment();
    }

    /**
     * Check if given channel has a staged receive
     */
    private boolean hasStagedReceive(BelinkChannel channel) {
        return stagedReceives.containsKey(channel);
    }

    /**
     * check if stagedReceives have unmuted channel
     */
    private boolean hasStagedReceives() {
        for (BelinkChannel channel : this.stagedReceives.keySet()) {
            if (!channel.isMute())
                return true;
        }
        return false;
    }


    /**
     * adds a receive to staged receives
     */
    private void addToStagedReceives(BelinkChannel channel, NetworkReceive receive) {
        if (!stagedReceives.containsKey(channel))
            stagedReceives.put(channel, new ArrayDeque<NetworkReceive>());

        Deque<NetworkReceive> deque = stagedReceives.get(channel);
        deque.add(receive);
    }

    /**
     * checks if there are any staged receives and adds to completedReceives
     */
    private void addToCompletedReceives() {
        if (!this.stagedReceives.isEmpty()) {
            Iterator<Map.Entry<BelinkChannel, Deque<NetworkReceive>>> iter = this.stagedReceives.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<BelinkChannel, Deque<NetworkReceive>> entry = iter.next();
                BelinkChannel channel = entry.getKey();
                if (!channel.isMute()) {
                    Deque<NetworkReceive> deque = entry.getValue();
                    addToCompletedReceives(channel, deque);
                    if (deque.isEmpty())
                        iter.remove();
                }
            }
        }
    }

    private void addToCompletedReceives(BelinkChannel channel, Deque<NetworkReceive> stagedDeque) {
        NetworkReceive networkReceive = stagedDeque.poll();
        this.completedReceives.add(networkReceive);
        this.sensors.recordBytesReceived(channel.id(), networkReceive.payload().limit());
    }

    private class SelectorMetrics {
        private final Metrics metrics;
        public final Sensor connectionClosed;
        public final Sensor connectionCreated;
        public final Sensor bytesTransferred;
        public final Sensor bytesSent;
        public final Sensor bytesReceived;
        public final Sensor selectTime;
        public final Sensor ioTime;

        /* Names of metrics that are not registered through sensors */
        private final List<MetricName> topLevelMetricNames = new ArrayList<>();
        private final List<Sensor> sensors = new ArrayList<>();

        public SelectorMetrics(Metrics metrics) {
            this.metrics = metrics;
            String metricGrpName = metricGrpPrefix + "-metrics";
            StringBuilder tagsSuffix = new StringBuilder();

            for (Map.Entry<String, String> tag: metricTags.entrySet()) {
                tagsSuffix.append(tag.getKey());
                tagsSuffix.append("-");
                tagsSuffix.append(tag.getValue());
            }

            this.connectionClosed = sensor("connections-closed:" + tagsSuffix.toString());
            MetricName metricName = metrics.metricName("connection-close-rate", metricGrpName, "Connections closed per second in the window.", metricTags);
            this.connectionClosed.add(metricName, new Rate());

            this.connectionCreated = sensor("connections-created:" + tagsSuffix.toString());
            metricName = metrics.metricName("connection-creation-rate", metricGrpName, "New connections established per second in the window.", metricTags);
            this.connectionCreated.add(metricName, new Rate());

            this.bytesTransferred = sensor("bytes-sent-received:" + tagsSuffix.toString());
            metricName = metrics.metricName("network-io-rate", metricGrpName, "The average number of network operations (reads or writes) on all connections per second.", metricTags);
            bytesTransferred.add(metricName, new Rate(new Count()));

            this.bytesSent = sensor("bytes-sent:" + tagsSuffix.toString(), bytesTransferred);
            metricName = metrics.metricName("outgoing-byte-rate", metricGrpName, "The average number of outgoing bytes sent per second to all servers.", metricTags);
            this.bytesSent.add(metricName, new Rate());
            metricName = metrics.metricName("request-rate", metricGrpName, "The average number of requests sent per second.", metricTags);
            this.bytesSent.add(metricName, new Rate(new Count()));
            metricName = metrics.metricName("request-size-avg", metricGrpName, "The average size of all requests in the window..", metricTags);
            this.bytesSent.add(metricName, new Avg());
            metricName = metrics.metricName("request-size-max", metricGrpName, "The maximum size of any request sent in the window.", metricTags);
            this.bytesSent.add(metricName, new Max());

            this.bytesReceived = sensor("bytes-received:" + tagsSuffix.toString(), bytesTransferred);
            metricName = metrics.metricName("incoming-byte-rate", metricGrpName, "Bytes/second read off all sockets", metricTags);
            this.bytesReceived.add(metricName, new Rate());
            metricName = metrics.metricName("response-rate", metricGrpName, "Responses received sent per second.", metricTags);
            this.bytesReceived.add(metricName, new Rate(new Count()));

            this.selectTime = sensor("select-time:" + tagsSuffix.toString());
            metricName = metrics.metricName("select-rate", metricGrpName, "Number of times the I/O layer checked for new I/O to perform per second", metricTags);
            this.selectTime.add(metricName, new Rate(new Count()));
            metricName = metrics.metricName("io-wait-time-ns-avg", metricGrpName, "The average length of time the I/O thread spent waiting for a socket ready for reads or writes in nanoseconds.", metricTags);
            this.selectTime.add(metricName, new Avg());
            metricName = metrics.metricName("io-wait-ratio", metricGrpName, "The fraction of time the I/O thread spent waiting.", metricTags);
            this.selectTime.add(metricName, new Rate(TimeUnit.NANOSECONDS));

            this.ioTime = sensor("io-time:" + tagsSuffix.toString());
            metricName = metrics.metricName("io-time-ns-avg", metricGrpName, "The average length of time for I/O per select call in nanoseconds.", metricTags);
            this.ioTime.add(metricName, new Avg());
            metricName = metrics.metricName("io-ratio", metricGrpName, "The fraction of time the I/O thread spent doing I/O", metricTags);
            this.ioTime.add(metricName, new Rate(TimeUnit.NANOSECONDS));

            metricName = metrics.metricName("connection-count", metricGrpName, "The current number of active connections.", metricTags);
            topLevelMetricNames.add(metricName);
            this.metrics.addMetric(metricName, new Measurable() {
                public double measure(MetricConfig config, long now) {
                    return channels.size();
                }
            });
        }

        private Sensor sensor(String name, Sensor... parents) {
            Sensor sensor = metrics.sensor(name, parents);
            sensors.add(sensor);
            return sensor;
        }

        public void maybeRegisterConnectionMetrics(String connectionId) {
            if (!connectionId.isEmpty() && metricsPerConnection) {
                // if one sensor of the metrics has been registered for the connection,
                // then all other sensors should have been registered; and vice versa
                String nodeRequestName = "node-" + connectionId + ".bytes-sent";
                Sensor nodeRequest = this.metrics.getSensor(nodeRequestName);
                if (nodeRequest == null) {
                    String metricGrpName = metricGrpPrefix + "-node-metrics";

                    Map<String, String> tags = new LinkedHashMap<>(metricTags);
                    tags.put("node-id", "node-" + connectionId);

                    nodeRequest = sensor(nodeRequestName);
                    MetricName metricName = metrics.metricName("outgoing-byte-rate", metricGrpName, tags);
                    nodeRequest.add(metricName, new Rate());
                    metricName = metrics.metricName("request-rate", metricGrpName, "The average number of requests sent per second.", tags);
                    nodeRequest.add(metricName, new Rate(new Count()));
                    metricName = metrics.metricName("request-size-avg", metricGrpName, "The average size of all requests in the window..", tags);
                    nodeRequest.add(metricName, new Avg());
                    metricName = metrics.metricName("request-size-max", metricGrpName, "The maximum size of any request sent in the window.", tags);
                    nodeRequest.add(metricName, new Max());

                    String nodeResponseName = "node-" + connectionId + ".bytes-received";
                    Sensor nodeResponse = sensor(nodeResponseName);
                    metricName = metrics.metricName("incoming-byte-rate", metricGrpName, tags);
                    nodeResponse.add(metricName, new Rate());
                    metricName = metrics.metricName("response-rate", metricGrpName, "The average number of responses received per second.", tags);
                    nodeResponse.add(metricName, new Rate(new Count()));

                    String nodeTimeName = "node-" + connectionId + ".latency";
                    Sensor nodeRequestTime = sensor(nodeTimeName);
                    metricName = metrics.metricName("request-latency-avg", metricGrpName, tags);
                    nodeRequestTime.add(metricName, new Avg());
                    metricName = metrics.metricName("request-latency-max", metricGrpName, tags);
                    nodeRequestTime.add(metricName, new Max());
                }
            }
        }

        public void recordBytesSent(String connectionId, long bytes) {
            long now = time.milliseconds();
            this.bytesSent.record(bytes, now);
            if (!connectionId.isEmpty()) {
                String nodeRequestName = "node-" + connectionId + ".bytes-sent";
                Sensor nodeRequest = this.metrics.getSensor(nodeRequestName);
                if (nodeRequest != null)
                    nodeRequest.record(bytes, now);
            }
        }

        public void recordBytesReceived(String connection, int bytes) {
            long now = time.milliseconds();
            this.bytesReceived.record(bytes, now);
            if (!connection.isEmpty()) {
                String nodeRequestName = "node-" + connection + ".bytes-received";
                Sensor nodeRequest = this.metrics.getSensor(nodeRequestName);
                if (nodeRequest != null)
                    nodeRequest.record(bytes, now);
            }
        }

        public void close() {
            for (MetricName metricName : topLevelMetricNames)
                metrics.removeMetric(metricName);
            for (Sensor sensor : sensors)
                metrics.removeSensor(sensor.name());
        }
    }

    // helper class for tracking least recently used connections to enable idle connection closing
    private static class IdleExpiryManager {
        private final Map<String, Long> lruConnections;
        private final long connectionsMaxIdleNanos;
        private long nextIdleCloseCheckTime;

        public IdleExpiryManager(Time time, long connectionsMaxIdleMs) {
            this.connectionsMaxIdleNanos = connectionsMaxIdleMs * 1000 * 1000;
            // initial capacity and load factor are default, we set them explicitly because we want to set accessOrder = true
            this.lruConnections = new LinkedHashMap<>(16, .75F, true);
            this.nextIdleCloseCheckTime = time.nanoseconds() + this.connectionsMaxIdleNanos;
        }

        public void update(String connectionId, long currentTimeNanos) {
            lruConnections.put(connectionId, currentTimeNanos);
        }

        public Map.Entry<String, Long> pollExpiredConnection(long currentTimeNanos) {
            if (currentTimeNanos <= nextIdleCloseCheckTime)
                return null;

            if (lruConnections.isEmpty()) {
                nextIdleCloseCheckTime = currentTimeNanos + connectionsMaxIdleNanos;
                return null;
            }

            Map.Entry<String, Long> oldestConnectionEntry = lruConnections.entrySet().iterator().next();
            Long connectionLastActiveTime = oldestConnectionEntry.getValue();
            nextIdleCloseCheckTime = connectionLastActiveTime + connectionsMaxIdleNanos;

            if (currentTimeNanos > nextIdleCloseCheckTime)
                return oldestConnectionEntry;
            else
                return null;
        }

        public void remove(String connectionId) {
            lruConnections.remove(connectionId);
        }
    }

}