package br.ufs.gothings.gateway;

import br.ufs.gothings.core.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.gateway.block.*;
import br.ufs.gothings.gateway.block.Package;
import br.ufs.gothings.gateway.block.Package.PackageInfo;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author Wagner Macedo
 */
public class InterconnectionController implements Block {
    private static Logger logger = LogManager.getFormatterLogger(InterconnectionController.class);

    private final CommunicationManager manager;
    private final Token accessToken;

    public InterconnectionController(final CommunicationManager manager, final Token accessToken) {
        this.manager = manager;
        this.accessToken = accessToken;
    }

    @Override
    public void receiveForwarding(final BlockId sourceId, final Package pkg) {
        final PackageInfo pkgInfo = pkg.getInfo(accessToken);

        switch (sourceId) {
            case INPUT_CONTROLLER:
                final GwRequest request = (GwRequest) pkgInfo.getMessage();
                final GwHeaders headers = request.headers();

                final URI uri;
                try {
                    uri = createURI(headers.get(GwHeaders.PATH));
                } catch (URISyntaxException e) {
                    if (logger.isErrorEnabled()) {
                        logger.error("could not parse URI from path sent by %s plugin: %s",
                                pkgInfo.getSourceProtocol(), e.getInput());
                    }
                    return;
                }

                final String targetProtocol = uri.getScheme();
                pkgInfo.setTargetProtocol(targetProtocol);

                final String target = uri.getRawAuthority();
                headers.set(GwHeaders.TARGET, target);

                final String targetAndPath = uri.getRawSchemeSpecificPart();
                final String path = StringUtils.replaceOnce(targetAndPath, target, "");
                headers.set(GwHeaders.PATH, path);

                final GwReply cached = getCache(request, targetProtocol);
                if (cached != null) {
                    pkgInfo.setMessage(cached);
                    manager.forward(this, BlockId.OUTPUT_CONTROLLER, pkg);
                } else {
                    manager.forward(this, BlockId.COMMUNICATION_MANAGER, pkg);
                }
                break;
            case COMMUNICATION_MANAGER:
                final GwReply reply = (GwReply) pkgInfo.getMessage();
                setCache(reply, pkgInfo.getSourceProtocol());
                manager.forward(this, BlockId.OUTPUT_CONTROLLER, pkg);
                break;
        }
    }

    private void setCache(final GwReply reply, final String protocol) {

    }

    private GwReply getCache(final GwRequest req, final String protocol) {
        if (req.headers().get(GwHeaders.OPERATION) != Operation.READ) {
            return null;
        }

        // TODO: Method stub
        return null;
    }

    private URI createURI(final String path) throws URISyntaxException {
        final String s_uri = path.replaceFirst("^/+", "").replaceFirst("/+", "://");
        final URIBuilder uri = new URIBuilder(s_uri);

        // sort query parameters
        final List<NameValuePair> params = uri.getQueryParams();
        params.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
        uri.setParameters(params);

        return uri.build();
    }
}
