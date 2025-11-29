package io.figchain.client.polling;

import io.figchain.avro.model.FigFamily;

import java.util.List;

@FunctionalInterface
public interface FcUpdateListener {
    void onUpdate(List<FigFamily> figFamilies);
}
