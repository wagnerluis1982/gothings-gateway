package br.ufs.gothings.gateway;

import br.ufs.gothings.core.common.Reason;
import br.ufs.gothings.core.message.GwHeaders;
import br.ufs.gothings.core.message.GwReply;
import br.ufs.gothings.core.message.GwRequest;
import br.ufs.gothings.core.message.headers.Operation;
import br.ufs.gothings.core.common.GatewayException;
import br.ufs.gothings.gateway.block.Block;
import br.ufs.gothings.gateway.block.BlockId;
import br.ufs.gothings.gateway.block.Package;
import br.ufs.gothings.gateway.block.Package.PackageInfo;
import br.ufs.gothings.gateway.block.Token;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Wagner Macedo
 */
public class InterconnectionController implements Block {
    private static final Logger logger = LogManager.getFormatterLogger(InterconnectionController.class);

    private final CommunicationManager manager;
    private final Token accessToken;

    private final ObserveList observeList = new ObserveList();

    public InterconnectionController(final CommunicationManager manager, final Token accessToken) {
        this.manager = manager;
        this.accessToken = accessToken;
    }

    @Override
    public void receiveForwarding(final BlockId sourceId, final Package pkg) {
        final PackageInfo pkgInfo = pkg.getInfo(accessToken);

        switch (sourceId) {
            case INPUT_CONTROLLER: {
                final GwRequest request = (GwRequest) pkgInfo.getMessage();
                final GwHeaders headers = request.headers();

                final URI uri;
                try {
                    uri = createURI(request);
                } catch (URISyntaxException e) {
                    if (logger.isErrorEnabled()) {
                        logger.error("could not parse URI from path sent by %s plugin: %s",
                                pkgInfo.getSourceProtocol(), e.getInput());
                    }
                    manager.handleError(this, new GatewayException(request, Reason.INVALID_URI));
                    return;
                }

                final String targetProtocol = uri.getScheme();
                pkgInfo.setTargetProtocol(targetProtocol);

                final String target = uri.getRawAuthority();
                headers.setTarget(target);

                final String targetAndPath = uri.getRawSchemeSpecificPart();
                final String path = StringUtils.replaceOnce(targetAndPath, "//" + target, "");
                headers.setPath(path);

                final Operation operation = headers.getOperation();
                final String s_uri = uri.toString();

                final GwReply cached = getCache(operation, s_uri);
                if (cached != null) {
                    cached.setSequence(request.getSequence());
                    pkgInfo.setMessage(cached);
                    manager.forward(this, BlockId.OUTPUT_CONTROLLER, pkg);
                } else {
                    switch (operation) {
                        case READ:
                        case OBSERVE:
                            observeList.add(s_uri, pkgInfo.getSourceProtocol(), request.getSequence());
                            break;
                        case UNOBSERVE:
                            // After remove permanent observer, if still there is any temporaries, prevent
                            // the UNOBSERVE message to go to the target plugin.
                            if (!observeList.remove(s_uri, pkgInfo.getSourceProtocol(), 0)) {
                                return;
                            }
                            break;
                    }
                    manager.forward(this, BlockId.COMMUNICATION_MANAGER, pkg);
                }
                break;
            }
            case COMMUNICATION_MANAGER: {
                final GwReply reply = (GwReply) pkgInfo.getMessage();
                final String sourceProtocol = pkgInfo.getSourceProtocol();

                try {
                    final URI uri = createURI(reply, sourceProtocol);
                    final Map<String, long[]> observers = observeList.get(uri.toString());
                    pkgInfo.setReplyTo(observers);
                } catch (URISyntaxException e) {
                    if (logger.isErrorEnabled()) {
                        logger.error("error on assembling URI from reply of %s plugin: %s",
                                sourceProtocol, e.getInput());
                    }
                    return;
                }

                setCache(reply, sourceProtocol);
                manager.forward(this, BlockId.OUTPUT_CONTROLLER, pkg);
                break;
            }
        }
    }

    private void setCache(final GwReply reply, final String protocol) {

    }

    private GwReply getCache(final Operation operation, final String uri) {
        if (operation != Operation.READ) {
            return null;
        }

        // TODO: Method stub
        return null;
    }

    private URI createURI(final GwRequest msg) throws URISyntaxException {
        final String path = msg.headers().getPath();
        final String s_uri = path.replaceFirst("^/+", "").replaceFirst("/+", "://");
        final URIBuilder uri = new URIBuilder(s_uri);

        // sort query parameters
        final List<NameValuePair> params = uri.getQueryParams();
        if (!params.isEmpty()) {
            params.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));
            uri.setParameters(params);
        }

        return uri.build();
    }

    private URI createURI(final GwReply msg, final String sourceProtocol) throws URISyntaxException {
        final String target = msg.headers().getTarget();
        final String path = msg.headers().getPath().replaceFirst("^/+", "");

        return new URI(String.format("%s://%s/%s", sourceProtocol, target, path));
    }

    public ObserveList getObserveList() {
        return observeList;
    }

    /**
     * Mapping of observing sequences to reply
     * <p>
     * Each observing is in the form: {@code [(uri, protocol, sequences)] where:
     * <ul>
     * <li>{@code uri} is a string used as the observing filter.
     * <li>{@code protocol} is the name of the interested protocol as registered by the plugin.
     * <li>{@code sequences} is a collection of message sequences to reply, this is useful for
     *      request/response protocols. If sequences is {@code null} the reply is sent to the
     *      plugin every time the gateway receives a reply with this filter.
     * </ul>
     */
    static class ObserveList {
        private final Map<String, Map<String, Set<Long>>> map = new ConcurrentHashMap<>();
        private final Lock lock = new ReentrantLock();

        private void add(final String uri, final String protocol, final long sequence) {
            final Map<String, Set<Long>> uriObserving = map.computeIfAbsent(uri, k -> new HashMap<>());
            lock.lock();
            final Set<Long> sequences = uriObserving.computeIfAbsent(protocol, k -> new HashSet<>());
            sequences.add(sequence);
            lock.unlock();
        }

        private Map<String, long[]> get(final String uri) {
            final Map<String, Set<Long>> uriObserving = map.get(uri);
            if (uriObserving != null) {
                Map<String, long[]> result = new HashMap<>();
                lock.lock();
                try {
                    for (final String key : uriObserving.keySet()) {
                        uriObserving.compute(key, (k, sequences) -> {
                            final long[] array = new long[sequences.size()];
                            result.put(key, array);

                            final Iterator<Long> it = sequences.iterator();
                            int i = 0;
                            while (it.hasNext()) {
                                final long next = it.next();
                                array[i++] = next;
                                if (next != 0) {
                                    it.remove();
                                }
                            }

                            if (sequences.isEmpty()) {
                                return null;
                            } else {
                                return sequences;
                            }
                        });
                    }
                    return result;
                } finally {
                    lock.unlock();
                }
            }
            throw new NoSuchElementException("no sequence observing " + uri);
        }

        /**
         * Remove the following observed sequence
         *
         * @param uri         the uri to find
         * @param protocol    the protocol associated to uri
         * @param sequence    the sequence associated to both uri and protocol
         * @return true if list of sequences was left empty, false otherwise.
         */
        private boolean remove(final String uri, final String protocol, final long sequence) {
            final Map<String, Set<Long>> uriObserving = map.get(uri);
            if (uriObserving != null) {
                lock.lock();
                try {
                    final Set<Long> sequences = uriObserving.get(protocol);
                    if (sequences != null) {
                        sequences.remove(sequence);
                        if (sequences.isEmpty()) {
                            uriObserving.remove(protocol);
                            return true;
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
            return true;
        }

        /**
         * Remove the sequence from the observing list.
         *
         * @param sequence    sequence to be removed
         */
        public void remove(final long sequence) {
            lock.lock();
            try {
                for (final Entry<String, Map<String, Set<Long>>> uriEntry : map.entrySet()) {
                    for (final Entry<String, Set<Long>> obsEntry : uriEntry.getValue().entrySet()) {
                        if (obsEntry.getValue().remove(sequence)) {
                            return;
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
