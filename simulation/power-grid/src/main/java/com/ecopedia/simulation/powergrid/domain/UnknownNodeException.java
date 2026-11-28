package com.ecopedia.simulation.powergrid.domain;

/** El nodo no existe en el sistema de la distribuidora. Sale como {@code UnknownNodeFault}. */
public class UnknownNodeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String nodeId;

    public UnknownNodeException(String nodeId) {
        super("Unknown node");
        this.nodeId = nodeId;
    }

    public String getNodeId() {
        return nodeId;
    }
}
