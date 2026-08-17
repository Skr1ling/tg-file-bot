package com.skriling.ierichonia;

import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public final class GameRenderer implements GLSurfaceView.Renderer {
    private static final String PREFS = "jerichonia_save_v1";

    private final SharedPreferences prefs;
    private final float[] projection = new float[16];
    private final float[] view = new float[16];
    private final float[] vp = new float[16];
    private final float[] model = new float[16];
    private final float[] mvp = new float[16];

    private FloatBuffer cubeBuffer;
    private int program;
    private int aPosition;
    private int aNormal;
    private int uMvp;
    private int uModel;
    private int uColor;

    private long lastFrameNs;
    private float autoSaveTimer;
    private float crystalSpin;

    private volatile float moveX;
    private volatile float moveY;
    private volatile float pendingLookX;
    private volatile float pendingLookY;
    private volatile boolean attackQueued;
    private volatile boolean dodgeQueued;

    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean dead;
    private volatile boolean objectiveReached;

    private volatile float playerX;
    private volatile float playerZ = 7f;
    private volatile float playerYaw;
    private volatile float cameraYaw;
    private volatile float cameraPitch = 24f;
    private volatile float hp = 100f;
    private volatile float stamina = 100f;
    private volatile float ether = 100f;

    private volatile boolean enemyAlive = true;
    private volatile float enemyX = 1.4f;
    private volatile float enemyZ = -9f;
    private volatile float enemyHp = 100f;

    private float attackCooldown;
    private float attackTimer;
    private float dodgeTimer;
    private float invulnerability;
    private float enemyAttackCooldown;

    private static final float CRYSTAL_X = 0f;
    private static final float CRYSTAL_Z = -28f;

    private static final float[] TREES = {
            -9f, 4f, -13f, 1f, -18f, -7f, -24f, 9f,
            8f, 7f, 12f, -3f, 10f, -14f, 14f, -22f,
            -12f, -27f, 11f, -30f, -16f, 8f, 17f, 2f
    };

    private static final float[] ROCKS = {
            -4f, 1f, 5f, -5f, -7f, 3f, -12f, 6f,
            5f, -15f, -7f, -21f, 7f, -25f, 15f, -11f
    };

    public GameRenderer(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        GLES20.glClearColor(0.105f, 0.16f, 0.19f, 1f);
        createCube();
        createProgram();
        lastFrameNs = System.nanoTime();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        float aspect = height == 0 ? 1f : (float) width / (float) height;
        Matrix.perspectiveM(projection, 0, 58f, aspect, 0.1f, 120f);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - lastFrameNs) / 1_000_000_000f);
        lastFrameNs = now;

        update(dt);
        render(dt);
    }

    private void update(float dt) {
        crystalSpin = (crystalSpin + dt * 38f) % 360f;
        if (!running || paused || dead) return;

        float lookX = pendingLookX;
        float lookY = pendingLookY;
        pendingLookX = 0f;
        pendingLookY = 0f;
        cameraYaw += lookX;
        cameraPitch = clamp(cameraPitch + lookY, 10f, 48f);

        attackCooldown = Math.max(0f, attackCooldown - dt);
        attackTimer = Math.max(0f, attackTimer - dt);
        dodgeTimer = Math.max(0f, dodgeTimer - dt);
        invulnerability = Math.max(0f, invulnerability - dt);
        enemyAttackCooldown = Math.max(0f, enemyAttackCooldown - dt);
        stamina = Math.min(100f, stamina + dt * (dodgeTimer > 0f ? 5f : 23f));

        float mx = moveX;
        float my = moveY;
        float len = (float) Math.sqrt(mx * mx + my * my);
        if (len > 1f) {
            mx /= len;
            my /= len;
        }

        float yawRad = (float) Math.toRadians(cameraYaw);
        float forwardX = (float) Math.sin(yawRad);
        float forwardZ = -(float) Math.cos(yawRad);
        float rightX = (float) Math.cos(yawRad);
        float rightZ = (float) Math.sin(yawRad);
        float dx = rightX * mx + forwardX * my;
        float dz = rightZ * mx + forwardZ * my;

        if (dodgeQueued) {
            dodgeQueued = false;
            if (stamina >= 30f && dodgeTimer <= 0f) {
                stamina -= 30f;
                dodgeTimer = 0.22f;
                invulnerability = 0.38f;
            }
        }

        if (dodgeTimer > 0f) {
            if (Math.abs(dx) + Math.abs(dz) < 0.1f) {
                float py = (float) Math.toRadians(playerYaw);
                dx = (float) Math.sin(py);
                dz = -(float) Math.cos(py);
            }
            playerX += dx * 11f * dt;
            playerZ += dz * 11f * dt;
        } else if (Math.abs(dx) + Math.abs(dz) > 0.02f) {
            playerX += dx * 4.6f * dt;
            playerZ += dz * 4.6f * dt;
            playerYaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
        }

        playerX = clamp(playerX, -20f, 20f);
        playerZ = clamp(playerZ, -33f, 11f);

        if (attackQueued) {
            attackQueued = false;
            if (attackCooldown <= 0f && stamina >= 10f) {
                stamina -= 10f;
                attackCooldown = 0.43f;
                attackTimer = 0.26f;
                tryHitEnemy();
            }
        }

        updateEnemy(dt);

        if (!objectiveReached && distance(playerX, playerZ, CRYSTAL_X, CRYSTAL_Z) < 2.35f) {
            objectiveReached = true;
            saveGame();
        }

        autoSaveTimer += dt;
        if (autoSaveTimer >= 4f) {
            autoSaveTimer = 0f;
            saveGame();
        }
    }

    private void tryHitEnemy() {
        if (!enemyAlive) return;
        float ex = enemyX - playerX;
        float ez = enemyZ - playerZ;
        float dist = (float) Math.sqrt(ex * ex + ez * ez);
        if (dist > 3.0f || dist < 0.001f) return;

        ex /= dist;
        ez /= dist;
        float py = (float) Math.toRadians(playerYaw);
        float fx = (float) Math.sin(py);
        float fz = -(float) Math.cos(py);
        float dot = fx * ex + fz * ez;
        if (dot > 0.15f) {
            enemyHp -= 34f;
            if (enemyHp <= 0f) {
                enemyHp = 0f;
                enemyAlive = false;
            }
        }
    }

    private void updateEnemy(float dt) {
        if (!enemyAlive) return;
        float dx = playerX - enemyX;
        float dz = playerZ - enemyZ;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.001f || dist > 15f) return;
        dx /= dist;
        dz /= dist;

        if (dist > 1.65f) {
            float speed = 2.15f;
            enemyX += dx * speed * dt;
            enemyZ += dz * speed * dt;
        } else if (enemyAttackCooldown <= 0f) {
            enemyAttackCooldown = 1.15f;
            if (invulnerability <= 0f) {
                hp -= 14f;
                if (hp <= 0f) {
                    hp = 0f;
                    dead = true;
                    running = false;
                    saveGame();
                }
            }
        }
    }

    private void render(float dt) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(program);

        float yaw = (float) Math.toRadians(cameraYaw);
        float pitch = (float) Math.toRadians(cameraPitch);
        float horizontal = (float) Math.cos(pitch) * 7.6f;
        float camX = playerX - (float) Math.sin(yaw) * horizontal;
        float camZ = playerZ + (float) Math.cos(yaw) * horizontal;
        float camY = 1.35f + (float) Math.sin(pitch) * 7.6f;
        Matrix.setLookAtM(view, 0, camX, camY, camZ, playerX, 1.1f, playerZ, 0f, 1f, 0f);
        Matrix.multiplyMM(vp, 0, projection, 0, view, 0);

        drawBox(0f, -0.4f, -10f, 25f, 0.35f, 48f, 0f, 0.18f, 0.26f, 0.20f);
        drawBox(0f, -0.02f, -10f, 2.4f, 0.025f, 44f, 0f, 0.31f, 0.25f, 0.18f);

        for (int i = 0; i < TREES.length; i += 2) {
            float x = TREES[i];
            float z = TREES[i + 1];
            drawBox(x, 1.15f, z, 0.34f, 2.4f, 0.34f, 0f, 0.28f, 0.20f, 0.10f);
            drawBox(x, 3.0f, z, 1.15f, 1.7f, 1.15f, 18f, 0.10f, 0.28f, 0.17f);
            drawBox(x + 0.45f, 3.55f, z - 0.25f, 0.85f, 1.2f, 0.85f, -22f, 0.12f, 0.34f, 0.19f);
        }

        for (int i = 0; i < ROCKS.length; i += 2) {
            float x = ROCKS[i];
            float z = ROCKS[i + 1];
            drawBox(x, 0.32f, z, 0.65f, 0.45f, 0.75f, (i * 17f) % 40f, 0.28f, 0.31f, 0.30f);
        }

        drawRuins();
        drawCrystal();
        drawPlayer();
        if (enemyAlive) drawEnemy();
    }

    private void drawRuins() {
        float stoneR = 0.34f, stoneG = 0.37f, stoneB = 0.36f;
        drawBox(-4.1f, 1.8f, -29.5f, 0.65f, 3.6f, 0.65f, 0f, stoneR, stoneG, stoneB);
        drawBox(4.1f, 1.8f, -29.5f, 0.65f, 3.6f, 0.65f, 0f, stoneR, stoneG, stoneB);
        drawBox(-4.1f, 1.25f, -24.5f, 0.7f, 2.5f, 0.7f, 0f, stoneR, stoneG, stoneB);
        drawBox(4.1f, 1.25f, -24.5f, 0.7f, 2.5f, 0.7f, 0f, stoneR, stoneG, stoneB);
        drawBox(0f, 3.65f, -29.5f, 4.75f, 0.48f, 0.65f, 0f, 0.31f, 0.34f, 0.33f);
        drawBox(-2.7f, 0.2f, -31.5f, 1.35f, 0.4f, 1.1f, 21f, 0.26f, 0.29f, 0.28f);
        drawBox(2.9f, 0.18f, -32f, 1.2f, 0.35f, 0.9f, -14f, 0.25f, 0.28f, 0.27f);
    }

    private void drawCrystal() {
        float pulse = 0.82f + 0.18f * (float) Math.sin(Math.toRadians(crystalSpin * 3f));
        drawBox(CRYSTAL_X, 1.25f, CRYSTAL_Z, 0.34f, 2.35f, 0.34f,
                crystalSpin, 0.25f * pulse, 0.90f * pulse, 1.00f * pulse);
        drawBox(CRYSTAL_X - 0.62f, 0.95f, CRYSTAL_Z + 0.25f, 0.22f, 1.5f, 0.22f,
                crystalSpin + 32f, 0.36f * pulse, 0.72f * pulse, 0.95f * pulse);
        drawBox(CRYSTAL_X + 0.65f, 0.82f, CRYSTAL_Z - 0.18f, 0.20f, 1.25f, 0.20f,
                crystalSpin - 27f, 0.52f * pulse, 0.54f * pulse, 1.00f * pulse);
    }

    private void drawPlayer() {
        drawBox(playerX, 1.05f, playerZ, 0.62f, 1.25f, 0.42f, playerYaw, 0.12f, 0.24f, 0.34f);
        drawBox(playerX, 2.02f, playerZ, 0.42f, 0.45f, 0.42f, playerYaw, 0.70f, 0.56f, 0.43f);
        float py = (float) Math.toRadians(playerYaw);
        float sx = playerX + (float) Math.cos(py) * 0.55f;
        float sz = playerZ + (float) Math.sin(py) * 0.55f;
        float swing = attackTimer > 0f ? 55f : 15f;
        drawBox(sx, 1.25f, sz, 0.12f, 0.95f, 0.10f, playerYaw + swing, 0.72f, 0.66f, 0.47f);
    }

    private void drawEnemy() {
        float dist = distance(playerX, playerZ, enemyX, enemyZ);
        float eyaw = dist > 0.05f ? (float) Math.toDegrees(Math.atan2(playerX - enemyX, -(playerZ - enemyZ))) : 0f;
        drawBox(enemyX, 1.0f, enemyZ, 0.70f, 1.35f, 0.50f, eyaw, 0.28f, 0.12f, 0.34f);
        drawBox(enemyX, 2.02f, enemyZ, 0.46f, 0.48f, 0.46f, eyaw, 0.47f, 0.28f, 0.52f);
        drawBox(enemyX - 0.55f, 1.1f, enemyZ, 0.14f, 0.85f, 0.14f, eyaw + 24f, 0.20f, 0.45f, 0.48f);
    }

    private void drawBox(float x, float y, float z,
                         float sx, float sy, float sz, float rotY,
                         float r, float g, float b) {
        Matrix.setIdentityM(model, 0);
        Matrix.translateM(model, 0, x, y, z);
        Matrix.rotateM(model, 0, rotY, 0f, 1f, 0f);
        Matrix.scaleM(model, 0, sx, sy, sz);
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0);

        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0);
        GLES20.glUniform4f(uColor, r, g, b, 1f);
        cubeBuffer.position(0);
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 24, cubeBuffer);
        GLES20.glEnableVertexAttribArray(aPosition);
        cubeBuffer.position(3);
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 24, cubeBuffer);
        GLES20.glEnableVertexAttribArray(aNormal);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36);
    }

    private void createProgram() {
        String vertex =
                "uniform mat4 uMVP;\n" +
                "uniform mat4 uModel;\n" +
                "attribute vec3 aPosition;\n" +
                "attribute vec3 aNormal;\n" +
                "varying vec3 vNormal;\n" +
                "void main(){\n" +
                "  gl_Position = uMVP * vec4(aPosition,1.0);\n" +
                "  vNormal = mat3(uModel) * aNormal;\n" +
                "}";
        String fragment =
                "precision mediump float;\n" +
                "uniform vec4 uColor;\n" +
                "varying vec3 vNormal;\n" +
                "void main(){\n" +
                "  vec3 n = normalize(vNormal);\n" +
                "  float light = max(dot(n, normalize(vec3(0.35,0.82,0.28))), 0.22);\n" +
                "  gl_FragColor = vec4(uColor.rgb * light, uColor.a);\n" +
                "}";
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertex);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragment);
        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vs);
        GLES20.glAttachShader(program, fs);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0);
        if (linked[0] == 0) throw new RuntimeException("OpenGL link failed: " + GLES20.glGetProgramInfoLog(program));
        aPosition = GLES20.glGetAttribLocation(program, "aPosition");
        aNormal = GLES20.glGetAttribLocation(program, "aNormal");
        uMvp = GLES20.glGetUniformLocation(program, "uMVP");
        uModel = GLES20.glGetUniformLocation(program, "uModel");
        uColor = GLES20.glGetUniformLocation(program, "uColor");
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("OpenGL shader failed: " + GLES20.glGetShaderInfoLog(shader));
        return shader;
    }

    private void createCube() {
        float[] v = {
                // front
                -0.5f,-0.5f, 0.5f, 0,0,1,   0.5f,-0.5f, 0.5f, 0,0,1,   0.5f, 0.5f, 0.5f, 0,0,1,
                -0.5f,-0.5f, 0.5f, 0,0,1,   0.5f, 0.5f, 0.5f, 0,0,1,  -0.5f, 0.5f, 0.5f, 0,0,1,
                // back
                 0.5f,-0.5f,-0.5f, 0,0,-1, -0.5f,-0.5f,-0.5f, 0,0,-1, -0.5f, 0.5f,-0.5f, 0,0,-1,
                 0.5f,-0.5f,-0.5f, 0,0,-1, -0.5f, 0.5f,-0.5f, 0,0,-1,  0.5f, 0.5f,-0.5f, 0,0,-1,
                // left
                -0.5f,-0.5f,-0.5f,-1,0,0,  -0.5f,-0.5f, 0.5f,-1,0,0,  -0.5f, 0.5f, 0.5f,-1,0,0,
                -0.5f,-0.5f,-0.5f,-1,0,0,  -0.5f, 0.5f, 0.5f,-1,0,0,  -0.5f, 0.5f,-0.5f,-1,0,0,
                // right
                 0.5f,-0.5f, 0.5f, 1,0,0,   0.5f,-0.5f,-0.5f, 1,0,0,   0.5f, 0.5f,-0.5f, 1,0,0,
                 0.5f,-0.5f, 0.5f, 1,0,0,   0.5f, 0.5f,-0.5f, 1,0,0,   0.5f, 0.5f, 0.5f, 1,0,0,
                // top
                -0.5f, 0.5f, 0.5f, 0,1,0,   0.5f, 0.5f, 0.5f, 0,1,0,   0.5f, 0.5f,-0.5f, 0,1,0,
                -0.5f, 0.5f, 0.5f, 0,1,0,   0.5f, 0.5f,-0.5f, 0,1,0,  -0.5f, 0.5f,-0.5f, 0,1,0,
                // bottom
                -0.5f,-0.5f,-0.5f, 0,-1,0,  0.5f,-0.5f,-0.5f, 0,-1,0,  0.5f,-0.5f, 0.5f, 0,-1,0,
                -0.5f,-0.5f,-0.5f, 0,-1,0,  0.5f,-0.5f, 0.5f, 0,-1,0, -0.5f,-0.5f, 0.5f, 0,-1,0
        };
        cubeBuffer = ByteBuffer.allocateDirect(v.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        cubeBuffer.put(v).position(0);
    }

    public void setMove(float x, float y) {
        moveX = x;
        moveY = y;
    }

    public void lookBy(float dxDegrees, float dyDegrees) {
        pendingLookX += dxDegrees;
        pendingLookY += dyDegrees;
    }

    public void requestAttack() { attackQueued = true; }
    public void requestDodge() { dodgeQueued = true; }

    public void newGame() {
        playerX = 0f;
        playerZ = 7f;
        playerYaw = 0f;
        cameraYaw = 0f;
        cameraPitch = 24f;
        hp = 100f;
        stamina = 100f;
        ether = 100f;
        enemyAlive = true;
        enemyX = 1.4f;
        enemyZ = -9f;
        enemyHp = 100f;
        objectiveReached = false;
        dead = false;
        paused = false;
        running = true;
        attackCooldown = attackTimer = dodgeTimer = invulnerability = enemyAttackCooldown = 0f;
        saveGame();
    }

    public void continueGame() {
        if (!hasSave()) {
            newGame();
            return;
        }
        playerX = prefs.getFloat("playerX", 0f);
        playerZ = prefs.getFloat("playerZ", 7f);
        playerYaw = prefs.getFloat("playerYaw", 0f);
        cameraYaw = prefs.getFloat("cameraYaw", 0f);
        cameraPitch = prefs.getFloat("cameraPitch", 24f);
        hp = prefs.getFloat("hp", 100f);
        stamina = prefs.getFloat("stamina", 100f);
        enemyAlive = prefs.getBoolean("enemyAlive", true);
        enemyX = prefs.getFloat("enemyX", 1.4f);
        enemyZ = prefs.getFloat("enemyZ", -9f);
        enemyHp = prefs.getFloat("enemyHp", 100f);
        objectiveReached = prefs.getBoolean("objectiveReached", false);
        dead = hp <= 0f;
        if (dead) {
            hp = 100f;
            playerX = 0f;
            playerZ = 7f;
            dead = false;
        }
        paused = false;
        running = true;
    }

    public void saveGame() {
        if (!running && !dead && !objectiveReached && !prefs.getBoolean("hasSave", false)) return;
        prefs.edit()
                .putBoolean("hasSave", true)
                .putFloat("playerX", playerX)
                .putFloat("playerZ", playerZ)
                .putFloat("playerYaw", playerYaw)
                .putFloat("cameraYaw", cameraYaw)
                .putFloat("cameraPitch", cameraPitch)
                .putFloat("hp", hp)
                .putFloat("stamina", stamina)
                .putBoolean("enemyAlive", enemyAlive)
                .putFloat("enemyX", enemyX)
                .putFloat("enemyZ", enemyZ)
                .putFloat("enemyHp", enemyHp)
                .putBoolean("objectiveReached", objectiveReached)
                .apply();
    }

    public boolean hasSave() { return prefs.getBoolean("hasSave", false); }
    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }
    public boolean isDead() { return dead; }
    public boolean isObjectiveReached() { return objectiveReached; }
    public float getHp() { return hp; }
    public float getStamina() { return stamina; }
    public float getEther() { return ether; }
    public float getEnemyHp() { return enemyHp; }
    public boolean isEnemyAlive() { return enemyAlive; }

    public void setPaused(boolean value) {
        paused = value;
        if (value) saveGame();
    }

    public void returnToMenu() {
        saveGame();
        running = false;
        paused = false;
    }

    private static float distance(float ax, float az, float bx, float bz) {
        float dx = ax - bx;
        float dz = az - bz;
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
