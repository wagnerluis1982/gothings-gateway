package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.GwPlugin;
import br.ufs.gothings.core.Settings;
import br.ufs.gothings.core.sink.Sink;
import br.ufs.gothings.core.sink.SinkLink;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Wagner Macedo
 */
public class HttpPlugin implements GwPlugin {

    static final String GW_PROTOCOL = "http";

    private final HttpPluginServer server;
    private final Settings settings;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Sink<GwMessage> srvSink = new Sink<>();

    public HttpPlugin() {
        server = new HttpPluginServer();
        settings = new Settings(started);
    }

    @Override
    public void start() {
        try {
            server.start(srvSink.getRightLink(), settings);
            started.set(true);
        } catch (InterruptedException ignored) {
            started.set(false);
        }
    }

    @Override
    public void stop() {
        try {
            server.stop();
            srvSink.stop();
            started.set(false);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public SinkLink<GwMessage> clientLink() {
        return null;
    }

    @Override
    public SinkLink<GwMessage> serverLink() {
        return srvSink.getLeftLink();
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
