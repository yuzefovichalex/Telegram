package org.telegram.ui.Components.share;

import static android.opengl.EGL14.EGL_NONE;
import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.GL_SRC_ALPHA;
import static android.opengl.GLES20.GL_TEXTURE_2D;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_S;
import static android.opengl.GLES20.GL_TEXTURE_WRAP_T;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glBlendFunc;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glTexParameteri;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glViewport;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;

import org.telegram.messenger.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class OpenGLBitmapProcessor {

    private final Context context;
    private int width;
    private int height;

    private boolean isInitialized;

    private EGLConfig[] configs = new EGLConfig[1];
    private EGLDisplay dpy;
    private EGLSurface surf;
    private EGLContext ctx;

    private int program;
    private int textureSizeHandle;

    private final float[] vertices = {
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f
    };

    private final float[] texCoords = {
        0.0f,  0.0f,
        1.0f,  0.0f,
        0.0f,  1.0f,
        1.0f,  1.0f
    };

    private final FloatBuffer vertexBuffer;
    private final FloatBuffer texCoordBuffer;

    private final int[] fboIds = new int[2];
    private final int[] textureIds = new int[2];

    public OpenGLBitmapProcessor(Context context) {
        this.context = context;

        vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices);
        vertexBuffer.position(0);

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords);
        texCoordBuffer.position(0);
    }

    public void onAttach() {
        if (isInitialized) {
            return;
        }

        initEGL();
        isInitialized = true;
    }

    private void initEGL() {
        dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] vers = new int[2];
        EGL14.eglInitialize(dpy, vers, 0, vers, 1);

        int[] configAttr = {
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL_NONE
        };
        configs = new EGLConfig[1];
        int[] numConfig = new int[1];
        EGL14.eglChooseConfig(dpy, configAttr, 0, configs, 0, 1, numConfig, 0);
        if (numConfig[0] == 0) {
            // TROUBLE! No config found.
        }
    }

    public void initSurface(int width, int height) {
        if (this.width == width && this.height == height) {
            return;
        }

        this.width = width;
        this.height = height;

        EGLConfig config = configs[0];
        if (config == null) {
            return;
        }

        if (surf != null) {
            EGL14.eglDestroySurface(dpy, surf);
            EGL14.eglDestroyContext(dpy, ctx);
            int error = EGL14.eglGetError();
            if (error != EGL14.EGL_SUCCESS) {
                Log.e("EGL", "Error creating surface: " + error);
            }
        }

        int[] surfAttr = {
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL_NONE
        };
        surf = EGL14.eglCreatePbufferSurface(dpy, config, surfAttr, 0);
        int error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) {
            Log.e("EGL", "Error creating surface: " + error);
        }

        int[] ctxAttrib = {
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL_NONE
        };
        ctx = EGL14.eglCreateContext(dpy, config, EGL14.EGL_NO_CONTEXT, ctxAttrib, 0);
    }

    private void setupFBOs(Bitmap src) {
        GLES20.glGenFramebuffers(2, fboIds, 0);
        GLES20.glGenTextures(2, textureIds, 0);

        for (int i = 0; i < 2; i++) {
            GLES20.glBindTexture(GL_TEXTURE_2D, textureIds[i]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLUtils.texImage2D(GL_TEXTURE_2D, 0, src, 0);
        }
    }

    private int createProgram() {
        int vert = ShaderUtils.createShader(context, GL_VERTEX_SHADER, R.raw.blur_vert);
        int frag = ShaderUtils.createShader(context, GL_FRAGMENT_SHADER, R.raw.blur_frag);
        return ShaderUtils.createProgram(vert, frag);
    }

    private int createMorphProgram() {
        int vert = ShaderUtils.createShader(context, GL_VERTEX_SHADER, R.raw.morph_vert);
        int frag = ShaderUtils.createShader(context, GL_FRAGMENT_SHADER, R.raw.morph_frag);
        return ShaderUtils.createProgram(vert, frag);
    }

    public void processBitmap(Bitmap src, Bitmap dst, float radius) {
        EGL14.eglMakeCurrent(dpy, surf, surf, ctx);
        glViewport(0, 0, src.getWidth(), src.getHeight());
        if (program == 0) {
            program = createProgram();
            textureSizeHandle = GLES20.glGetUniformLocation(program, "u_TexelOffset");
        }
        setupFBOs(src);
        blur(src, radius);
        readPixels(dst);
        cleanup();
        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
    }

    public void drawMorph(
        Bitmap dst,
        float rectX,
        float rectY,
        float rectW,
        float rectH,
        float rectR,
        float cx,
        float cy,
        float cr,
        float progress
    ) {
        EGL14.eglMakeCurrent(dpy, surf, surf, ctx);
        if (program == 0) {
            program = createMorphProgram();

        }

        glViewport(0, 0, dst.getWidth(), dst.getHeight());

        glUseProgram(program);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "u_ViewportSize"), dst.getWidth(), dst.getHeight());
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "rectSize"), rectW, rectH);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "rectPos"), rectX, rectY);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "rectRadius"), rectR);
        GLES20.glUniform3f(GLES20.glGetUniformLocation(program, "circleData"), cx, cy, cr);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "progress"), progress);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        setupVertexAttributes();

        readPixels(dst);
        cleanup();
        EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
    }

    private float toVertexX(float x) {
        return 2 * x - 1;
    }

    private float toVertexY(float y) {
        return 1 - 2 * y;
    }

    private void blur(Bitmap src, float radius) {
//        float ltx = 0f, lty = 0f, lbx = 0f, rty = 0f;
//        float rtx = (float) src.getWidth() / width, rbx = (float) src.getWidth() / width;
//        float lby = (float) src.getHeight() / height, rby = (float) src.getHeight() / height;

//        float[] vertices = {
//            toVertexX(rbx), toVertexY(rby),
//            toVertexX(lbx), toVertexY(lby),
//            toVertexX(ltx), toVertexY(lty),
//            toVertexX(rtx), toVertexY(rty),
//        };

        glEnable(GL_BLEND);

        // 1 pass - X
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureIds[0], 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(program);
        GLES20.glUniform2f(textureSizeHandle, 0, 1f / src.getHeight());
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "u_Radius"), radius);
        setupVertexAttributes();

        // 2 pass - Y
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboIds[1]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureIds[1], 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIds[0]);

        GLES20.glUseProgram(program);
        GLES20.glUniform2f(textureSizeHandle, 1f / src.getWidth(), 0);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "u_Radius"), radius);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        setupVertexAttributes();
    }

    private void setupVertexAttributes() {
        vertexBuffer.position(0);
        texCoordBuffer.position(0);

        int positionHandle = GLES20.glGetAttribLocation(program, "a_Position");
        int texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord");

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }

    private void readPixels(Bitmap dst) {
        int width = dst.getWidth();
        int height = dst.getHeight();
        int[] pixels = new int[width * height];
        IntBuffer buffer = IntBuffer.wrap(pixels);
        buffer.position(0);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer);
        dst.copyPixelsFromBuffer(buffer);
    }

    private void cleanup() {
        GLES20.glDeleteTextures(2, textureIds, 0);
        GLES20.glDeleteFramebuffers(2, fboIds, 0);
        GLES20.glDeleteProgram(program);
        program = 0;
    }

    public void onDetach() {
        EGL14.eglDestroySurface(dpy, surf);
        EGL14.eglDestroyContext(dpy, ctx);
        EGL14.eglTerminate(dpy);
        isInitialized = false;
    }
}


