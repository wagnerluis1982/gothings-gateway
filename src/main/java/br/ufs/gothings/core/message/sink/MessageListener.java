package br.ufs.gothings.core.message.sink;

import br.ufs.gothings.core.message.DataMessage;

/**
 * @author Wagner Macedo
 */
public interface MessageListener {
    void valueReceived(DataMessage msg) throws Exception;
}
