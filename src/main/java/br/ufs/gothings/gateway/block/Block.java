package br.ufs.gothings.gateway.block;

/**
 * @author Wagner Macedo
 */
public interface Block {
    void process(Package pkg) throws Exception;
}
