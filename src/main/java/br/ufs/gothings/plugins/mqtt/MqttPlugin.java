package br.ufs.gothings.plugins.mqtt;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.GwPlugin;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.sink.Sink;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class MqttPlugin implements GwPlugin {

    static final String GW_PROTOCOL = "mqtt";

    private final MqttPluginClient client;
    private final Settings settings;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Sink<GwMessage> cliSink;
    private final Sink<GwMessage> srvSink;

    public MqttPlugin() {
        cliSink = new Sink<>();
        srvSink = null;
        settings = new Settings(started);

        client = new MqttPluginClient(cliSink);
    }

    @Override
    public void start() {
        started.set(true);
    }

    @Override
    public void stop() {
        cliSink.stop();
        srvSink.stop();
        started.set(false);
    }

    @Override
    public Sink<GwMessage> clientSink() {
        return cliSink;
    }

    @Override
    public Sink<GwMessage> serverSink() {
        return srvSink;
    }

    @Override
    public String getProtocol() {
        return GW_PROTOCOL;
    }

    @Override
    public Settings settings() {
        return settings;
    }
}