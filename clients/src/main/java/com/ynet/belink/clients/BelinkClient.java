package com.ynet.belink.clients;

import com.ynet.belink.common.Node;
import com.ynet.belink.common.requests.AbstractRequest;

import java.util.List;

/**
 * Created by goldratio on 27/06/2017.
 */
public interface BelinkClient {
    boolean ready(Node node, long now);

    void close(String nodeId);

    long connectionDelay(Node node, long now);

    boolean connectionFailed(Node node);

    boolean isReady(Node node, long now);

    void send(ClientRequest request, long now);

    List<ClientResponse> poll(long timeout, long now);

    int inFlightRequestCount();

    boolean hasInFlightRequests();

    int inFlightRequestCount(String node);

    boolean hasInFlightRequests(String node);

    void wakeup();

    void close();

    Node leastLoadedNode(long now);

    ClientRequest newClientRequest(String nodeId, AbstractRequest.Builder<?> requestBuilder, long createdTimeMs,
                                   boolean expectResponse);

    ClientRequest newClientRequest(String nodeId, AbstractRequest.Builder<?> requestBuilder, long createdTimeMs,
                                   boolean expectResponse, RequestCompletionHandler callback);
}
