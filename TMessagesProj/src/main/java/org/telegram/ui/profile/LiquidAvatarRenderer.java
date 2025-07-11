package org.telegram.ui.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class LiquidAvatarRenderer {

    @NonNull
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @NonNull
    private final Context context;

    private boolean isInitialized, isInitializing, isDestroying;

    private boolean areResourcesInitialized;

    @Nullable
    private HandlerThread rendererThread;

    @Nullable
    private Handler renderHandler;

    @Nullable
    private EGL10 egl;

    @Nullable
    private EGLDisplay eglDisplay;

    @Nullable
    private EGLConfig eglConfig;

    @Nullable
    private EGLContext eglContext;

    @Nullable
    private EGLSurface eglSurface;

    @Nullable
    private ImageReader imageReader;

    private int width, height;

    private int programInit, programJfa, programFinal;

    private int shapeTexture;

    @Nullable
    private Framebuffer ping, pong;

    private int quadVbo, quadVao;

    @NonNull
    private Path shape = new Path();

    @Nullable
    private Callback callback;


    public LiquidAvatarRenderer(@NonNull Context context) {
        this.context = context;
    }


    public void setShape(@NonNull Path shape) {
        if (this.shape == shape) {
            return;
        }

        this.shape = shape;
        if (renderHandler != null) {
            renderHandler.post(this::createShapeJfa);
        }
    }

    public void setCallback(@Nullable Callback callback) {
        this.callback = callback;
    }

    public void init() {
        if (isInitialized || isInitializing) {
            return;
        }

        rendererThread = new HandlerThread("LiquidAvatarRenderer");
        rendererThread.start();
        renderHandler = new Handler(rendererThread.getLooper());

        isInitializing = true;
        renderHandler.post(() -> {
            initEGL();
            isInitializing = false;
            isInitialized = true;
        });
    }

    private void initEGL() {
        egl = (EGL10) EGLContext.getEGL();

        eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        egl.eglInitialize(eglDisplay, version);

        int[] configAttribs = {
            EGL10.EGL_RENDERABLE_TYPE, 4, // EGL_OPENGL_ES2_BIT
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 0,
            EGL10.EGL_STENCIL_SIZE, 0,
            EGL10.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfig = new int[1];
        egl.eglChooseConfig(eglDisplay, configAttribs, configs, 1, numConfig);
        eglConfig = configs[0];

        int[] contextAttribs = {
            0x3098, 3, // EGL_CONTEXT_CLIENT_VERSION, 3
            EGL10.EGL_NONE
        };
        eglContext = egl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, contextAttribs);

        if (width > 0 && height > 0) {
            dispatchSizeChanged(width, height);
        }
    }

    public void setSize(int width, int height) {
        if (!isInitialized && !isInitializing || this.width == width && this.height == height) {
            return;
        }

        this.width = width;
        this.height = height;

        if (renderHandler != null) {
            renderHandler.post(() -> dispatchSizeChanged(width, height));
        }
    }

    private void dispatchSizeChanged(int width, int height) {
        EGL10 egl = this.egl;
        if (egl == null) {
            return;
        }

        if (eglSurface != null) {
            egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
            egl.eglDestroySurface(eglDisplay, eglSurface);
        }
        if (imageReader != null) {
            imageReader.close();
        }

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        imageReader.setOnImageAvailableListener(this::dispatchBitmapReady, renderHandler);
        Surface surface = imageReader.getSurface();
        eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, surface, null);
        egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);

        if (!areResourcesInitialized) {
            createProgramsAndBuffers();
            areResourcesInitialized = true;
        }

        ping = new Framebuffer(width, height);
        pong = new Framebuffer(width, height);

        createShapeJfa();
    }

    private void dispatchBitmapReady(@NonNull ImageReader reader) {
        if (callback == null) {
            return;
        }

        Image image = reader.acquireLatestImage();
        if (image == null) {
            return;
        }

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;

        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        image.close();

        mainHandler.post(() -> callback.onBitmapReady(bitmap));
    }

    private void createProgramsAndBuffers() {
        float[] vertices = {
            -1f, -1f,
            1f, -1f,
            -1f,  1f,
            1f, -1f,
            1f,  1f,
            -1f,  1f
        };

        int[] buffers = new int[1];
        GLES30.glGenBuffers(1, buffers, 0);
        quadVbo = buffers[0];
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo);
        FloatBuffer fb = ByteBuffer.allocateDirect(vertices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        fb.put(vertices).position(0);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.length * 4, fb, GLES30.GL_STATIC_DRAW);

        int[] vaos = new int[1];
        GLES30.glGenVertexArrays(1, vaos, 0);
        quadVao = vaos[0];
        GLES30.glBindVertexArray(quadVao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0);
        GLES30.glBindVertexArray(0);

        programInit = ShaderUtils.createProgram(context, R.raw.path_morph_vert, R.raw.path_morph_init_frag);
        programJfa = ShaderUtils.createProgram(context, R.raw.path_morph_vert, R.raw.path_morph_jfa_frag);
        programFinal = ShaderUtils.createProgram(context, R.raw.path_morph_vert, R.raw.path_morph_final_frag);
    }

    private void createShapeJfa() {
        if (width == 0 && height == 0) {
            return;
        }

        if (shapeTexture != 0) {
            GLES30.glDeleteTextures(1, new int[] { shapeTexture }, 0);
        }

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.BLACK);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);

        canvas.drawPath(shape, paint);

        int[] tex = new int[1];
        GLES30.glGenTextures(1, tex, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0]);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);

        bitmap.recycle();

        shapeTexture = tex[0];
        GLES30.glClearColor(0f, 0f, 0f, 1f);
        renderInit();
        runJfaOnce();
    }

    private void renderInit() {
        if (ping == null) {
            return;
        }

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ping.fbo);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        GLES30.glUseProgram(programInit);
        GLES30.glBindVertexArray(quadVao);

        int shapeLoc = GLES30.glGetUniformLocation(programInit, "uShape");
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shapeTexture);
        GLES30.glUniform1i(shapeLoc, 0);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
        GLES30.glBindVertexArray(0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    private void runJfaOnce() {
        if (ping == null || pong == null) {
            return;
        }

        int steps = (int) Math.ceil(Math.log(Math.max(width, height)) / Math.log(2));
        int step = 1 << (steps - 1);
        Framebuffer src = ping;
        Framebuffer dst = pong;

        while (step >= 1) {
            renderJfa(src, dst, (float) step / width);
            Framebuffer tmp = src;
            src = dst;
            dst = tmp;
            step /= 2;
        }

        if (src != ping) {
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, src.fbo);
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, ping.fbo);
            GLES30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        }
    }

    private void renderJfa(Framebuffer src, Framebuffer dst, float step) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dst.fbo);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        GLES30.glUseProgram(programJfa);
        GLES30.glBindVertexArray(quadVao);

        int prevLoc = GLES30.glGetUniformLocation(programJfa, "uPrev");
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, src.texture);
        GLES30.glUniform1i(prevLoc, 0);

        int stepLoc = GLES30.glGetUniformLocation(programJfa, "uStep");
        GLES30.glUniform1f(stepLoc, step);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
        GLES30.glBindVertexArray(0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    public void requestRender(float dropX, float dropY, float radius) {
        if (renderHandler != null) {
            renderHandler.post(() ->
                render(dropX / width, dropY / height, radius / width, .06f)
            );
        }
    }

    private void render(float dropX, float dropY, float radius, float k) {
        if (egl == null || ping == null) {
            return;
        }

        GLES30.glClearColor(0f, 0f, 0f, 0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);

        GLES30.glViewport(0, 0, width, height);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        GLES30.glUseProgram(programFinal);
        GLES30.glBindVertexArray(quadVao);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ping.texture);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programFinal, "uJFA"), 0);

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shapeTexture);
        GLES30.glUniform1i(GLES30.glGetUniformLocation(programFinal, "uShape"), 1);

        GLES30.glUniform2f(GLES30.glGetUniformLocation(programFinal, "uDrop"), dropX, dropY);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programFinal, "uRadius"), radius);
        GLES30.glUniform1f(GLES30.glGetUniformLocation(programFinal, "uK"), k);
        int flipYLoc = GLES30.glGetUniformLocation(programFinal, "uFlipY");
        GLES30.glUniform1i(flipYLoc, 1);

        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6);
        egl.eglSwapBuffers(eglDisplay, eglSurface);
        GLES30.glBindVertexArray(0);
    }

    public void release() {
        if (!isInitialized || isDestroying) {
            return;
        }

        isDestroying = true;
        if (renderHandler != null) {
            renderHandler.post(() -> {
                releaseEGL();
                isDestroying = false;
                isInitialized = false;
            });
        }
    }

    private void releaseEGL() {
        if (egl != null) {
            egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        }

        GLES30.glDeleteBuffers(1, new int[]{ quadVbo }, 0);
        GLES30.glDeleteVertexArrays(1, new int[]{ quadVao }, 0);

        GLES30.glDeleteTextures(1, new int[]{ shapeTexture }, 0);
        if (ping != null) {
            ping.release();
        }
        if (pong != null) {
            pong.release();
        }

        GLES30.glDeleteProgram(programInit);
        GLES30.glDeleteProgram(programJfa);
        GLES30.glDeleteProgram(programFinal);

        areResourcesInitialized = false;

        if (egl != null) {
            if (eglSurface != EGL10.EGL_NO_SURFACE) {
                egl.eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = null;
            }

            if (eglContext != EGL10.EGL_NO_CONTEXT) {
                egl.eglDestroyContext(eglDisplay, eglContext);
                eglContext = null;
            }

            if (eglDisplay != null) {
                egl.eglTerminate(eglDisplay);
                eglDisplay = null;
            }
            egl = null;
        }

        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        if (rendererThread != null) {
            rendererThread.quitSafely();
            rendererThread = null;
        }
        renderHandler = null;
    }


    public interface Callback {
        void onBitmapReady(Bitmap bmp);
    }

    private static class Framebuffer {
        int fbo, texture;

        public Framebuffer(int w, int h) {
            int[] fbos = new int[1];
            int[] textures = new int[1];
            GLES30.glGenFramebuffers(1, fbos, 0);
            GLES30.glGenTextures(1, textures, 0);
            fbo = fbos[0];
            texture = textures[0];

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h, 0,
                GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, texture, 0);

            int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                throw new RuntimeException("Framebuffer not complete, status: " + status);
            }

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        }

        public void release() {
            GLES30.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            GLES30.glDeleteTextures(1, new int[]{texture}, 0);
        }
    }

}
