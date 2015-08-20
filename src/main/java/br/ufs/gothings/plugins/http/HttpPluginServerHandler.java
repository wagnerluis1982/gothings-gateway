package br.ufs.gothings.plugins.http;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.GwMessage;
import br.ufs.gothings.core.message.ComplexHeader;
import br.ufs.gothings.core.message.Operation;
import br.ufs.gothings.core.sink.SinkLink;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static io.netty.handler.codec.http.HttpHeaders.Names.ACCEPT;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Values.KEEP_ALIVE;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author Wagner Macedo
 */
final class HttpPluginServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final SinkLink<GwMessage> sinkLink;

    HttpPluginServerHandler(SinkLink<GwMessage> sinkLink) {
        this.sinkLink = sinkLink;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (HttpHeaders.is100ContinueExpected(request)) {
            send100Continue(ctx);
        }

        final FullHttpResponse response = new DefaultFullHttpResponse(request.getProtocolVersion(), OK);

        final GwMessage gw_request = parseHttpRequest(request);
        if (gw_request != null) {
            final long sequence = sinkLink.put(gw_request);
            try {
                final GwMessage gw_response = sinkLink.get(sequence, 1, TimeUnit.MINUTES);
                response.content().writeBytes(gw_response.payload());
                fillHttpResponseHeaders(response.headers(), gw_response.headers());
            }
            // Handling with possible errors
            catch (InterruptedException e) {
                ctx.close();
                return;
            } catch (TimeoutException e) {
                response.setStatus(REQUEST_TIMEOUT);
            }
        }
        // If no request was created then the used HTTP method is not allowed
        else {
            response.setStatus(METHOD_NOT_ALLOWED);
        }

        // Handling keep-alive setting
        final boolean keepAlive = HttpHeaders.isKeepAlive(request);
        if (keepAlive) {
            response.headers().set(CONNECTION, KEEP_ALIVE);
        }

        final ChannelFuture future = ctx.writeAndFlush(response);
        if (!keepAlive) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static GwMessage parseHttpRequest(FullHttpRequest request) {
        final HttpHeaders headers = request.headers();

        final String method = request.getMethod().name();
        switch (method) {
            case "GET":case "PUT":case "POST":case "DELETE":
                final GwMessage msg = new GwMessage();
                final GwHeaders gw_headers = msg.headers();
                gw_headers.path().setValue(request.getUri());

                switch (method) {
                    case "GET":
                        gw_headers.operation().setValue(Operation.GET);
                        addExpectedTypes(gw_headers, headers);
                        break;
                    case "PUT":
                        gw_headers.operation().setValue(Operation.PUT);
                        gw_headers.contentType().setValue(headers.get(CONTENT_TYPE));
                        msg.setPayload(request.content());
                        break;
                    case "POST":
                        gw_headers.operation().setValue(Operation.POST);
                        gw_headers.contentType().setValue(headers.get(CONTENT_TYPE));
                        msg.setPayload(request.content());
                        break;
                    case "DELETE":
                        gw_headers.operation().setValue(Operation.DELETE);
                        break;
                }

                return msg;
        }
        return null;
    }

    private static void addExpectedTypes(GwHeaders gw_headers, HttpHeaders headers) {
        final String acceptValues = headers.get(ACCEPT);
        if (acceptValues != null) {
            final ComplexHeader<String> expectedTypes = gw_headers.expectedTypes();
            for (String type : acceptValues.split(",")) {
                final int pos = type.indexOf(';');
                expectedTypes.add(pos != -1 ? type.substring(0, pos) : type);
            }
        }
    }

    private static void fillHttpResponseHeaders(HttpHeaders hh, GwHeaders gwh) {
        setHttpHeader(hh, CONTENT_TYPE, gwh.contentType().getValue());
    }

    private static void setHttpHeader(HttpHeaders hh, String key, CharSequence value) {
        if (value != null) {
            hh.set(key, value);
        }
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        final FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
