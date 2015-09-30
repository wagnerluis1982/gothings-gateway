package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.common.Reason;
import br.ufs.gothings.core.message.GwError;
import br.ufs.gothings.core.message.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.plugin.ReplyLink;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.paho.client.mqttv3.*;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.eclipse.paho.client.mqttv3.MqttException.*;

/**
 * @author Wagner Macedo
 */
public final class MqttPluginClient {
    private static final Logger logger = LogManager.getFormatterLogger(MqttPluginClient.class);

    private final Map<String, MqttConnection> connections;
    private final ReplyLink replyLink;
    private volatile boolean closed = false;

    MqttPluginClient(final ReplyLink replyLink) {
        this.replyLink = replyLink;
        connections = new ConcurrentHashMap<>();
    }

    void sendRequest(final GwRequest request) {
        if (!closed) {
            final String host = request.headers().getTarget();
            try {
                final MqttConnection conn = getMqttConnection(host);
                conn.sendMessage(request);
            } catch (MqttException e) {
                switch (e.getReasonCode()) {
                    case REASON_CODE_CLIENT_EXCEPTION:
                        if (!(e.getCause() instanceof UnknownHostException)) {
                            break;
                        }
                    case REASON_CODE_SERVER_CONNECT_ERROR:
                    case REASON_CODE_BROKER_UNAVAILABLE:
                    case REASON_CODE_CLIENT_TIMEOUT:
                    case REASON_CODE_CONNECTION_LOST:
                        replyLink.sendError(new GwError(request, Reason.TARGET_NOT_FOUND));
                        break;
                }
                replyLink.sendError(new GwError(request, Reason.OTHER));
            }
        } else {
            replyLink.sendError(new GwError(request, Reason.OTHER));
        }
    }

    /**
     * Close all connections and stop to receive requests
     */
    void close() {
        closed = true;
        connections.forEach((host, connection) -> {
            try {
                connection.client.disconnect().waitForCompletion();
            } catch (MqttException e) {
                logger.warn("problem disconnecting %s", host);
            }
        });
    }

    private MqttConnection getMqttConnection(final String host) throws MqttException {
        try {
            return connections.computeIfAbsent(host, k -> new MqttConnection(host));
        } catch (RuntimeException e) {
            if (e.getCause() instanceof MqttException) {
                throw ((MqttException) e.getCause());
            } else {
                throw e;
            }
        }
    }

    private class MqttConnection {
        private final MqttAsyncClient client;
        private final IMqttToken connectionToken;

        public MqttConnection(String host) throws RuntimeException {
            try {
                client = new MqttAsyncClient("tcp://" + host, "gothings-client_" + host.hashCode());
                client.setCallback(new MqttCallback() {
                    // TODO: try to reconnect on fail
                    @Override
                    public void connectionLost(Throwable cause) {

                    }

                    @Override
                    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
                        final GwReply msg = new GwReply();
                        msg.payload().set(mqttMessage.getPayload());
                        final GwHeaders h = msg.headers();
                        h.setTarget(host);
                        h.setPath(topic);

                        replyLink.send(msg);
                    }

                    // TODO: what to do here?
                    @Override
                    public void deliveryComplete(IMqttDeliveryToken token) {

                    }
                });
                connectionToken = client.connect(new MqttConnectOptions());
                connectionToken.waitForCompletion();
            } catch (MqttException e) {
                throw new RuntimeException(e);
            }
        }

        public void sendMessage(GwRequest msg) throws MqttException {
            final GwHeaders h = msg.headers();
            final Operation operation = h.getOperation();
            final String topic = h.getPath();
            final int qos = max(0, min(2, h.getQoS()));
            switch (operation) {
                // CREATE or UPDATE is mapped as a publish, but this plugin treats CREATE as a retained message
                case CREATE:
                case UPDATE:
                    final MqttMessage mqttMessage = new MqttMessage(msg.payload().asBytes());
                    mqttMessage.setRetained(operation == Operation.CREATE);
                    mqttMessage.setQos(qos);
                    client.publish(topic, mqttMessage);
                    break;

                // MQTT doesn't really specify a delete operation, but when a retained message with a zero byte payload
                // is sent, the broker removes the retained message
                case DELETE:
                    client.publish(topic, ArrayUtils.EMPTY_BYTE_ARRAY, 0, true);
                    break;

                // READ and OBSERVE are unsurprisingly directly mapped to subscribe
                case READ:
                case OBSERVE:
                    client.subscribe(topic, qos);
                    break;

                // UNOBSERVE is the equivalent of unsubscribe
                case UNOBSERVE:
                    client.unsubscribe(topic);
                    break;
            }
        }
    }
}
