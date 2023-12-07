package org.telegram.ui.Components.DeleteEffect;

import static android.opengl.GLES10.glBindTexture;
import static android.opengl.GLES11.glTexParameteri;
import static android.opengl.GLES20.GL_BLEND;
import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_FRAGMENT_SHADER;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_ONE_MINUS_SRC_ALPHA;
import static android.opengl.GLES20.GL_POINTS;
import static android.opengl.GLES20.GL_SRC_ALPHA;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE_MIN_FILTER;
import static android.opengl.GLES20.GL_TRIANGLE_FAN;
import static android.opengl.GLES20.GL_VERTEX_SHADER;
import static android.opengl.GLES20.glActiveTexture;
import static android.opengl.GLES20.glBlendFunc;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glDisableVertexAttribArray;
import static android.opengl.GLES20.glDrawArrays;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glEnableVertexAttribArray;
import static android.opengl.GLES20.glGenTextures;
import static android.opengl.GLES20.glGetAttribLocation;
import static android.opengl.GLES20.glGetError;
import static android.opengl.GLES20.glGetUniformLocation;
import static android.opengl.GLES20.glUniform1i;
import static android.opengl.GLES20.glUseProgram;
import static android.opengl.GLES20.glVertexAttrib1f;
import static android.opengl.GLES20.glVertexAttribPointer;
import static android.opengl.GLES20.glViewport;
import static javax.microedition.khronos.opengles.GL10.GL_FLOAT;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_2D;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MAG_FILTER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Cells.ChatMessageCell;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class DeleteMessageEffectRenderer implements GLSurfaceView.Renderer {

    private final int particleSize;
    private final int animateColumnPerFrame;

    private final Random random = new Random();

    private final Context context;

    private int targetContainerWidth;
    private int targetContainerHeight;

    private int particlesProgramId;

    private int particlesInitialPositionLocation;
    private int particlesTargetPositionLocation;
    private int particlesTimeLocation;
    private int particlesColorAttribLocation;

    private int textureProgramId;

    // Texture position on the surface [-1, 1][-1, 1]
    private int texturePositionAttribLocation;
    // Texture coordinates inside the positioned rectangles [0, 1][0, 1]
    private int textureCoordinatesAttribLocation;
    private int textureUniformLocation;

    private float[] textureVertices;
    private float[] textureCoordinates;
    private float[] particlesInitialVertices;
    private float[] particlesTargetVertices;
    private float[] particlesTime;
    private float[] particlesColors;

    private FloatBuffer textureVertexBuffer;
    private FloatBuffer textureCoordinatesBuffer;
    private FloatBuffer particlesInitialVertexBuffer;
    private FloatBuffer particlesTargetVertexBuffer;
    private FloatBuffer particlesTimeBuffer;
    private FloatBuffer particlesColorBuffer;

    private ConcurrentLinkedQueue<RenderInfo> renderInfos = new ConcurrentLinkedQueue<>();


    public DeleteMessageEffectRenderer(
        @NonNull Context context,
        int targetContainerWidth,
        int targetContainerHeight
    ) {
        this.context = context;

        this.targetContainerWidth = targetContainerWidth;
        this.targetContainerHeight = targetContainerHeight;

        int performanceClass = SharedConfig.getDevicePerformanceClass();
        if (performanceClass == SharedConfig.PERFORMANCE_CLASS_HIGH) {
            this.particleSize = 3;
            this.animateColumnPerFrame = 10;
        } else if (performanceClass == SharedConfig.PERFORMANCE_CLASS_AVERAGE) {
            this.particleSize = 4;
            this.animateColumnPerFrame = 6;
        } else {
            this.particleSize = 8;
            this.animateColumnPerFrame = 2;
        }
    }


    private int getParticleSize() {
        return particleSize;
    }

    private int getAnimateColumnPerFrame() {
        return animateColumnPerFrame;
    }

    public int getTargetContainerWidth() {
        return targetContainerWidth;
    }

    public void setTargetContainerWidth(int targetContainerWidth) {
        this.targetContainerWidth = targetContainerWidth;
    }

    public int getTargetContainerHeight() {
        return targetContainerHeight;
    }

    public void setTargetContainerHeight(int targetContainerHeight) {
        this.targetContainerHeight = targetContainerHeight;
    }

    @Override
    public void onSurfaceCreated(GL10 arg0, EGLConfig arg1) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int particlesVertexShaderId = ShaderUtils.createShader(context, GL_VERTEX_SHADER, R.raw.particle_vert);
        int particlesFragmentShaderId = ShaderUtils.createShader(context, GL_FRAGMENT_SHADER, R.raw.particle_frag);
        particlesProgramId = ShaderUtils.createProgram(particlesVertexShaderId, particlesFragmentShaderId);

        particlesColorAttribLocation = glGetAttribLocation(particlesProgramId, "a_Color");
        particlesInitialPositionLocation = glGetAttribLocation(particlesProgramId, "a_InitialPosition");
        particlesTargetPositionLocation = glGetAttribLocation(particlesProgramId, "a_TargetPosition");
        particlesTimeLocation = glGetAttribLocation(particlesProgramId, "a_Time");

        int aPointSize = glGetAttribLocation(particlesProgramId, "a_PointSize");
        glVertexAttrib1f(aPointSize, particleSize);

        int textureVertexShaderId = ShaderUtils.createShader(context, GL_VERTEX_SHADER, R.raw.texture_vert);
        int textureFragmentShaderId = ShaderUtils.createShader(context, GL_FRAGMENT_SHADER, R.raw.texture_frag);
        textureProgramId = ShaderUtils.createProgram(textureVertexShaderId, textureFragmentShaderId);

        texturePositionAttribLocation = glGetAttribLocation(textureProgramId, "a_Position");
        textureCoordinatesAttribLocation = glGetAttribLocation(textureProgramId, "a_TextureCoordinates");
        textureUniformLocation = glGetUniformLocation(textureProgramId, "u_Texture");
    }

    @Override
    public void onSurfaceChanged(GL10 arg0, int width, int height) {
        glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 arg0) {
        glClear(GL_COLOR_BUFFER_BIT);

        if (renderInfos.isEmpty()) {
            return;
        }

        cleanUpRenderers();

        for (RenderInfo renderInfo : renderInfos) {
            renderInfo.loadTextureIfNeeded();
        }

        drawTexture();
        drawParticles();
    }

    private void cleanUpRenderers() {
        Iterator<RenderInfo> iterator = renderInfos.iterator();
        while (iterator.hasNext()) {
            RenderInfo renderInfo = iterator.next();
            if (renderInfo.isFinished) {
                iterator.remove();
            }
        }
    }

    private void drawTexture() {
        checkTextureBuffers();

        glUseProgram(textureProgramId);
        glEnableVertexAttribArray(texturePositionAttribLocation);
        glEnableVertexAttribArray(textureCoordinatesAttribLocation);

        glVertexAttribPointer(texturePositionAttribLocation, 2, GL_FLOAT, false, 8, textureVertexBuffer);
        glVertexAttribPointer(textureCoordinatesAttribLocation, 2, GL_FLOAT, false, 8, textureCoordinatesBuffer);

        glUniform1i(textureUniformLocation, 0);

        glActiveTexture(GL_TEXTURE0);

        int i = 0;
        for (RenderInfo renderInfo : renderInfos) {
            glBindTexture(GL_TEXTURE_2D, renderInfo.textureId);
            glDrawArrays(GL_TRIANGLE_FAN, i * 4, 4);
            i++;
        }

        glDisableVertexAttribArray(texturePositionAttribLocation);
        glDisableVertexAttribArray(textureCoordinatesAttribLocation);
    }

    private void checkTextureBuffers() {
        int texturesPointsCount = renderInfos.size() * 4;
        if (textureVertices == null || textureVertices.length != texturesPointsCount * 2) {
            textureVertices = new float[texturesPointsCount * 2];
        }

        int offset = 0;
        for (RenderInfo renderInfo : renderInfos) {
            float[] vertices = renderInfo.textureVertices;
            copyElements(vertices, textureVertices, offset);
            offset += vertices.length;
        }

        textureVertexBuffer = updateBuffer(textureVertexBuffer, textureVertices);

        if (textureCoordinates == null || textureCoordinates.length != texturesPointsCount * 2) {
            textureCoordinates = new float[texturesPointsCount * 2];
        }

        offset = 0;
        for (RenderInfo renderInfo : renderInfos) {
            float[] coordinates = renderInfo.textureCoordinates;
            copyElements(coordinates, textureCoordinates, offset);
            offset += coordinates.length;
        }

        textureCoordinatesBuffer = updateBuffer(textureCoordinatesBuffer, textureCoordinates);
    }

    private void drawParticles() {
        glUseProgram(particlesProgramId);

        for (RenderInfo renderInfo : renderInfos) {
            renderInfo.animateParticles();
        }

        checkParticlesBuffers();

        glVertexAttribPointer(particlesTimeLocation, 2, GL_FLOAT, false, 0, particlesTimeBuffer);
        glEnableVertexAttribArray(particlesTimeLocation);

        glVertexAttribPointer(particlesInitialPositionLocation, 2, GL_FLOAT, false, 0, particlesInitialVertexBuffer);
        glEnableVertexAttribArray(particlesInitialPositionLocation);

        glVertexAttribPointer(particlesTargetPositionLocation, 2, GL_FLOAT, false, 0, particlesTargetVertexBuffer);
        glEnableVertexAttribArray(particlesTargetPositionLocation);

        glVertexAttribPointer(particlesColorAttribLocation, 4, GL_FLOAT, false, 0, particlesColorBuffer);
        glEnableVertexAttribArray(particlesColorAttribLocation);

        int offset = 0;
        for (RenderInfo renderInfo : renderInfos) {
            if (renderInfo.dataIsReady) {
                glDrawArrays(GL_POINTS, offset, renderInfo.getCurrentAnimatedParticlesCount());
            }
            offset += renderInfo.getParticlesCount();
        }

        glDisableVertexAttribArray(particlesColorAttribLocation);
        glDisableVertexAttribArray(particlesInitialPositionLocation);
        glDisableVertexAttribArray(particlesTargetPositionLocation);
        glDisableVertexAttribArray(particlesTimeLocation);
    }

    public void checkParticlesBuffers() {
        int totalParticlesCount = 0;
        for (RenderInfo renderInfo : renderInfos) {
            totalParticlesCount += renderInfo.getParticlesCount();
        }

        if (particlesInitialVertices == null || particlesInitialVertices.length != totalParticlesCount * 2) {
            particlesInitialVertices = new float[totalParticlesCount * 2];
        }

        int offset = 0;
        for (RenderInfo renderInfo : renderInfos) {
            if (renderInfo.dataIsReady) {
                float[] particles = renderInfo.particlesInitialVertices;
                copyElements(particles, particlesInitialVertices, offset);
            }
            offset += renderInfo.getParticlesCount() * 2;
        }

        particlesInitialVertexBuffer = updateBuffer(particlesInitialVertexBuffer, particlesInitialVertices);

        if (particlesTargetVertices == null || particlesTargetVertices.length != totalParticlesCount * 2) {
            particlesTargetVertices = new float[totalParticlesCount * 2];
        }

        offset = 0;
        for (RenderInfo renderInfo : renderInfos) {
            if (renderInfo.dataIsReady) {
                float[] particles = renderInfo.particlesTargetVertices;
                copyElements(particles, particlesTargetVertices, offset);
            }
            offset += renderInfo.getParticlesCount() * 2;
        }

        particlesTargetVertexBuffer = updateBuffer(particlesTargetVertexBuffer, particlesTargetVertices);

        if (particlesTime == null || particlesTime.length != totalParticlesCount * 2) {
            particlesTime = new float[totalParticlesCount * 2];
        }

        offset = 0;
        for (RenderInfo renderInfo : renderInfos) {
            if (renderInfo.dataIsReady) {
                float[] time = renderInfo.particlesTime;
                copyElements(time, particlesTime, offset);
            }
            offset += renderInfo.getParticlesCount() * 2;
        }

        particlesTimeBuffer = updateBuffer(particlesTimeBuffer, particlesTime);

        if (particlesColors == null || particlesColors.length != totalParticlesCount * 4) {
            particlesColors = new float[totalParticlesCount * 4];
        }

        offset = 0;
        for (RenderInfo renderInfo : renderInfos) {
            if (renderInfo.dataIsReady) {
                float[] colors = renderInfo.particlesColors;
                copyElements(colors, particlesColors, offset);
            }
            offset += renderInfo.getParticlesCount() * 4;
        }

        particlesColorBuffer = updateBuffer(particlesColorBuffer, particlesColors);
    }

    private void copyElements(float[] source, float[] destination, int offset) {
        System.arraycopy(
            source,
            0,
            destination,
            offset,
            source.length
        );
    }

    @NonNull
    private FloatBuffer updateBuffer(@Nullable FloatBuffer buffer, float[] array) {
        if (buffer == null || buffer.capacity() != array.length * 4) {
            buffer = createFloatBuffer(array);
        } else {
            buffer.clear();
            buffer.put(array);
            buffer.position(0);
        }
        return buffer;
    }

    public void composeView(ChatMessageCell cell, int[] viewLocation) {
        RenderInfo renderInfo = new RenderInfo(this, random);
        renderInfos.add(renderInfo);
        renderInfo.composeView(cell, viewLocation);
    }

    /**
     * Convert x-coordinate from [0, screenWidth] to [-1, 1].
     * */
    private float normalizeX(float x) {
        return 2f * x / getTargetContainerWidth() - 1f;
    }

    /**
     * Convert y-coordinate from [0, screenHeight] to [-1, 1].
     * */
    private float normalizeY(float y) {
        return 1f - 2f * y / getTargetContainerHeight();
    }

    @NonNull
    private FloatBuffer createFloatBuffer(@NonNull float[] floats) {
        FloatBuffer buffer = ByteBuffer
            .allocateDirect(floats.length * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer();
        buffer.put(floats);
        buffer.position(0);
        return buffer;
    }




    public static class RenderInfo {

        private final DeleteMessageEffectRenderer renderer;

        private final Random random;

        private int columnCount;
        private int rowCount;

        private final float[] textureVertices = {
            -1.0f, 1.0f,  // top-left
            -1.0f, -1.0f, // bottom-left
            1.0f, -1.0f,  // bottom-right
            1.0f, 1.0f    // top-right
        };
        private final float[] textureCoordinates = {
            0.0f, 0.0f,   // top-left
            0.0f, 1.0f,   // bottom-left
            1.0f, 1.0f,   // bottom-right
            1.0f, 0.0f    // top-right
        };
        private float[] particlesInitialVertices;
        private float[] particlesTargetVertices;
        private float[] particlesTime;
        private float[] particlesColors;

        private int lastAnimatedColumn = 1;

        private boolean dataIsReady = false;

        @Nullable
        private Bitmap sourceBitmap;

        private long lastUpdateTime = -1;

        private int textureId;

        private boolean isFinished;


        public RenderInfo(
            @NonNull DeleteMessageEffectRenderer renderer,
            @NonNull Random random
        ) {
            this.renderer = renderer;
            this.random = random;
        }


        public int getParticlesCount() {
            return rowCount * columnCount;
        }

        public int getCurrentAnimatedParticlesCount() {
            return lastAnimatedColumn * rowCount;
        }

        public void loadTextureIfNeeded() {
            if (textureId == 0 && sourceBitmap != null) {
                loadTexture();
            }
        }

        private void loadTexture() {
            Bitmap sourceBitmap = this.sourceBitmap;
            if (sourceBitmap == null || sourceBitmap.isRecycled()) {
                throw new IllegalStateException("Source bitmap can't be used: null or recycled.");
            }

            final int[] textureHandle = new int[1];
            glGenTextures(1, textureHandle, 0);

            if (textureHandle[0] != 0) {
                glBindTexture(GL_TEXTURE_2D, textureHandle[0]);

                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

                GLUtils.texImage2D(GL_TEXTURE_2D, 0, sourceBitmap, 0);

                this.sourceBitmap = null;
            }

            if (textureHandle[0] == 0) {
                throw new RuntimeException("Error loading texture.");
            }

            textureId = textureHandle[0];
        }

        public void animateParticles() {
            if (!dataIsReady || isFinished) {
                return;
            }

            if (lastUpdateTime == -1) {
                lastUpdateTime = System.currentTimeMillis();
            }

            int doneCount = 0;
            for (int i = 0; i < (lastAnimatedColumn + 1) * rowCount * 2; i += 2) {
                // Alpha channel
                if (particlesColors[i * 2 + 3] == 0) {
                    doneCount++;
                    continue;
                }

                particlesTime[i] = Math.min(particlesTime[i] + (int) (System.currentTimeMillis() - lastUpdateTime), particlesTime[i + 1]);

                float elapsed = particlesTime[i];
                float targetTime = particlesTime[i + 1];
                if (elapsed == 0f && targetTime == 0f) {
                    particlesTargetVertices[i] = (random.nextInt(200) - 100) / 100f;
                    particlesTargetVertices[i + 1] = (random.nextInt(100) + random.nextInt(100)) / 100f;
                    particlesTime[i] = 0f;
                    particlesTime[i + 1] = 1800f + random.nextInt(1800);
                }

                if (particlesTime[i] == particlesTime[i + 1]) {
                    doneCount++;
                }
            }

            lastUpdateTime = System.currentTimeMillis();
            lastAnimatedColumn = Math.min(lastAnimatedColumn + renderer.getAnimateColumnPerFrame(), columnCount - 1);

            float width = renderer.getParticleSize() * 2f * renderer.getAnimateColumnPerFrame() / renderer.getTargetContainerWidth();
            textureVertices[0] = Math.min(textureVertices[0] + width, 1f);
            textureVertices[2] = Math.min(textureVertices[2] + width, 1f);

            float w = (float) renderer.getAnimateColumnPerFrame() / columnCount;
            textureCoordinates[0] = Math.min(textureCoordinates[0] + w, 1f);
            textureCoordinates[2] = Math.min(textureCoordinates[2] + w, 1f);

            isFinished = doneCount == getParticlesCount();
        }

        public void composeView(ChatMessageCell cell, int[] viewLocation) {
            dataIsReady = false;

            lastAnimatedColumn = 1;

            Bitmap viewBitmap = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(viewBitmap);
            cell.drawCachedBackground(c);
            cell.draw(c);

            cell.setVisibility(View.INVISIBLE);

            textureVertices[0] = renderer.normalizeX(viewLocation[0]);
            textureVertices[1] = renderer.normalizeY(viewLocation[1]);
            textureVertices[2] = textureVertices[0];
            textureVertices[3] = renderer.normalizeY(viewLocation[1] + cell.getHeight());
            textureVertices[4] = renderer.normalizeX(viewLocation[0] + cell.getWidth());
            textureVertices[5] = textureVertices[3];
            textureVertices[6] = textureVertices[4];
            textureVertices[7] = textureVertices[1];
            textureId = 0;
            sourceBitmap = viewBitmap;

            textureCoordinates[0] = 0f;
            textureCoordinates[2] = 0f;

            columnCount = cell.getWidth() / renderer.getParticleSize();
            rowCount = cell.getHeight() / renderer.getParticleSize();
            int particlesCount = columnCount * rowCount;

            particlesColors = new float[particlesCount * 4];
            particlesInitialVertices = new float[particlesCount * 2];
            particlesTargetVertices = new float[particlesCount * 2];
            particlesTime = new float[particlesCount * 2];

            int threadCount = Runtime.getRuntime().availableProcessors();
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            int countPerThread = columnCount / threadCount;
            int rest = columnCount % threadCount;
            for (int i = 0; i < threadCount; i++) {
                int startIndex = i * countPerThread;
                int offset = countPerThread;
                if (i == threadCount - 1 && rest != 0) {
                    offset += rest;
                }
                ImageProcessingTask task = new ImageProcessingTask(
                    this,
                    viewBitmap,
                    startIndex, offset, rowCount,
                    viewLocation[0], viewLocation[1]
                );
                executorService.submit(task);
            }

            executorService.shutdown();

            try {
                boolean terminated = executorService.awaitTermination(1, TimeUnit.MINUTES);
                if (!terminated) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }

            dataIsReady = true;
        }

        private void updatePoint(int idx, float x, float y) {
            particlesInitialVertices[idx] = x;
            particlesInitialVertices[idx + 1] = y;
            particlesTargetVertices[idx] = x;
            particlesTargetVertices[idx + 1] = y;
        }

        private void updateColor(int idx, float red, float green, float blue, float alpha) {
            particlesColors[idx] = red;
            particlesColors[idx + 1] = green;
            particlesColors[idx + 2] = blue;
            particlesColors[idx + 3] = alpha;
        }

        /**
         * Convert x-coordinate from [0, screenWidth] to [-1, 1].
         * */
        private float normalizeX(float x) {
            return renderer.normalizeX(x);
        }

        /**
         * Convert y-coordinate from [0, screenHeight] to [-1, 1].
         * */
        private float normalizeY(float y) {
            return renderer.normalizeY(y);
        }

    }

    private static class ImageProcessingTask implements Runnable {

        @NonNull
        private final RenderInfo renderInfo;

        @NonNull
        private final Bitmap sourceBitmap;

        private final int startIndex;

        private final int offset;

        private final int rowCount;

        private final int viewLocationX, viewLocationY;

        private final int particleSize;


        public ImageProcessingTask(
            @NonNull RenderInfo renderInfo,
            @NonNull Bitmap sourceBitmap,
            int startIndex,
            int offset,
            int rowCount,
            int viewLocationX,
            int viewLocationY
        ) {
            this.renderInfo = renderInfo;
            this.sourceBitmap = sourceBitmap;
            this.startIndex = startIndex;
            this.offset = offset;
            this.rowCount = rowCount;
            this.viewLocationX = viewLocationX;
            this.viewLocationY = viewLocationY;
            this.particleSize = renderInfo.renderer.getParticleSize();
        }


        @Override
        public void run() {
            int[] chunkColors = new int[particleSize * particleSize];
            float[] resultColor = new float[4];
            // Offset all points before taking into account two values (x and y)
            int idx = startIndex * rowCount * 2;
            for (int i = startIndex; i < startIndex + offset; i++) {
                for (int j = 0; j < rowCount; j++) {
                    sourceBitmap.getPixels(chunkColors, 0, particleSize, i * particleSize, j * particleSize, particleSize, particleSize);

                    float x = renderInfo.normalizeX(i * particleSize + particleSize / 2f + viewLocationX);
                    float y = renderInfo.normalizeY(j * particleSize + particleSize / 2f + viewLocationY);
                    renderInfo.updatePoint(idx, x, y);

                    ColorUtils.blendColors(chunkColors, resultColor);
                    renderInfo.updateColor(
                        idx * 2,
                        resultColor[0],
                        resultColor[1],
                        resultColor[2],
                        resultColor[3]
                    );

                    idx += 2;
                }
            }
        }

    }

}
