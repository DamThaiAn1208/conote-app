package com.conote.client.util;

import javafx.scene.Parent;

public record LoadedView<T>(Parent root, T controller) {
}
