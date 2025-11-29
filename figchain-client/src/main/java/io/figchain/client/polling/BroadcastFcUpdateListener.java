package io.figchain.client.polling;

import io.figchain.avro.model.FigFamily;

import java.util.List;

public class BroadcastFcUpdateListener implements FcUpdateListener {

    private final List<FcUpdateListener> listeners;

    public BroadcastFcUpdateListener(List<FcUpdateListener> listeners) {
        this.listeners = listeners;
    }

    @Override
    public void onUpdate(List<FigFamily> figFamilies) {
        for (FcUpdateListener listener : listeners) {
            listener.onUpdate(figFamilies);
        }
    }
}
