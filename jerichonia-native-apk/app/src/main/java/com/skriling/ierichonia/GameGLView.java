package com.skriling.ierichonia;

import android.content.Context;
import android.opengl.GLSurfaceView;

public final class GameGLView extends GLSurfaceView {
    public GameGLView(Context context, GameRenderer renderer) {
        super(context);
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }
}
