package org.elasticsearch.plugin.readonlyrest.store;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.plugin.readonlyrest.builder.request.PutKeyRequest;
import org.elasticsearch.plugin.readonlyrest.key.KeyDescriptor;

public class KeyStoreService {

    public void putKey(PutKeyRequest request, KeyDescriptor keyDescriptor, ActionListener<Boolean> actionListener) {

    }
}
