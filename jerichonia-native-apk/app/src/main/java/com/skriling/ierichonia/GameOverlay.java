package com.skriling.ierichonia;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

public final class GameOverlay extends View {
    private final MainActivity activity;
    private final GameRenderer renderer;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    private boolean mainMenu = true;
    private boolean pauseMenu;

    private final RectF newGameRect = new RectF();
    private final RectF continueRect = new RectF();
    private final RectF resumeRect = new RectF();
    private final RectF saveMenuRect = new RectF();
    private final RectF deathContinueRect = new RectF();

    private int joystickPointer = -1;
    private int lookPointer = -1;
    private float joystickCenterX;
    private float joystickCenterY;
    private float joystickRadius;
    private float joystickKnobX;
    private float joystickKnobY;
    private float lastLookX;
    private float lastLookY;

    public GameOverlay(MainActivity activity, GameRenderer renderer) {
        super(activity);
        this.activity = activity;
        this.renderer = renderer;
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
    }

    private float dp(float value) { return value * density; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        joystickCenterX = dp(105);
        joystickCenterY = h - dp(105);
        joystickRadius = dp(65);
        if (joystickPointer < 0) {
            joystickKnobX = joystickCenterX;
            joystickKnobY = joystickCenterY;
        }

        if (mainMenu) {
            drawMainMenu(canvas, w, h);
        } else if (renderer.isDead()) {
            drawHud(canvas, w, h);
            drawDeath(canvas, w, h);
        } else {
            drawHud(canvas, w, h);
            if (pauseMenu || renderer.isPaused()) drawPause(canvas, w, h);
        }

        postInvalidateOnAnimation();
    }

    private void drawMainMenu(Canvas c, int w, int h) {
        paint.setColor(Color.rgb(10, 16, 21));
        c.drawRect(0, 0, w, h, paint);

        paint.setColor(Color.rgb(23, 37, 43));
        c.drawCircle(w * 0.18f, h * 0.72f, dp(190), paint);
        paint.setColor(Color.rgb(15, 29, 35));
        c.drawCircle(w * 0.85f, h * 0.18f, dp(230), paint);

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.rgb(218, 178, 99));
        paint.setTextSize(dp(34));
        paint.setFakeBoldText(true);
        c.drawText("ИЕРИХОНИЯ", w / 2f, h * 0.28f, paint);

        paint.setFakeBoldText(false);
        paint.setColor(Color.rgb(180, 213, 219));
        paint.setTextSize(dp(15));
        c.drawText("ОСКОЛОК СУДЬБЫ", w / 2f, h * 0.35f, paint);

        float bw = Math.min(dp(260), w * 0.42f);
        float bh = dp(52);
        float left = w / 2f - bw / 2f;
        float top = h * 0.49f;
        newGameRect.set(left, top, left + bw, top + bh);
        continueRect.set(left, top + bh + dp(14), left + bw, top + bh * 2 + dp(14));
        drawMenuButton(c, newGameRect, "НОВАЯ ИГРА", true);
        drawMenuButton(c, continueRect, "ПРОДОЛЖИТЬ", renderer.hasSave());

        paint.setTextSize(dp(11));
        paint.setColor(Color.argb(170, 210, 225, 228));
        c.drawText("Vertical Slice • Android", w / 2f, h - dp(28), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawMenuButton(Canvas c, RectF r, String text, boolean enabled) {
        paint.setColor(enabled ? Color.rgb(49, 69, 72) : Color.rgb(35, 39, 40));
        c.drawRoundRect(r, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(enabled ? Color.rgb(211, 169, 91) : Color.rgb(85, 88, 89));
        c.drawRoundRect(r, dp(8), dp(8), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(15));
        paint.setFakeBoldText(true);
        paint.setColor(enabled ? Color.WHITE : Color.GRAY);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = r.centerY() - (fm.ascent + fm.descent) / 2f;
        c.drawText(text, r.centerX(), y, paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawHud(Canvas c, int w, int h) {
        float margin = dp(24);
        drawBar(c, margin, dp(24), dp(205), dp(15), renderer.getHp() / 100f,
                Color.rgb(167, 57, 58), "ЖИЗНЬ");
        drawBar(c, margin, dp(48), dp(180), dp(10), renderer.getStamina() / 100f,
                Color.rgb(198, 164, 78), "");
        drawBar(c, margin, dp(65), dp(150), dp(8), renderer.getEther() / 100f,
                Color.rgb(71, 164, 194), "");

        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(dp(13));
        paint.setFakeBoldText(true);
        paint.setColor(Color.WHITE);
        String objective = renderer.isObjectiveReached()
                ? "ОСКОЛОК СУДЬБЫ НАЙДЕН"
                : "ЦЕЛЬ: ДОБЕРИТЕСЬ ДО ДРЕВНИХ РУИН";
        c.drawText(objective, w / 2f, dp(34), paint);
        paint.setFakeBoldText(false);

        if (renderer.isEnemyAlive() && renderer.getEnemyHp() < 100f) {
            float ew = Math.min(dp(260), w * 0.34f);
            float ex = w / 2f - ew / 2f;
            drawBar(c, ex, dp(49), ew, dp(8), renderer.getEnemyHp() / 100f,
                    Color.rgb(132, 70, 150), "РАЗЛОМЛЕННЫЙ");
        }

        drawJoystick(c);
        drawActionButtons(c, w, h);

        if (renderer.isObjectiveReached()) {
            paint.setColor(Color.argb(205, 7, 13, 17));
            RectF banner = new RectF(w * 0.21f, h * 0.35f, w * 0.79f, h * 0.57f);
            c.drawRoundRect(banner, dp(12), dp(12), paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.rgb(105, 223, 238));
            paint.setFakeBoldText(true);
            paint.setTextSize(dp(21));
            c.drawText("ОСКОЛОК ОТКЛИКНУЛСЯ", w / 2f, banner.top + dp(45), paint);
            paint.setFakeBoldText(false);
            paint.setColor(Color.WHITE);
            paint.setTextSize(dp(13));
            c.drawText("Прогресс сохранён. Vertical Slice завершён.", w / 2f, banner.top + dp(76), paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawBar(Canvas c, float x, float y, float width, float height, float fraction, int color, String label) {
        fraction = Math.max(0f, Math.min(1f, fraction));
        paint.setColor(Color.argb(190, 8, 11, 13));
        c.drawRoundRect(new RectF(x, y, x + width, y + height), height / 2f, height / 2f, paint);
        paint.setColor(color);
        c.drawRoundRect(new RectF(x, y, x + width * fraction, y + height), height / 2f, height / 2f, paint);
        if (!label.isEmpty()) {
            paint.setTextSize(dp(9));
            paint.setColor(Color.WHITE);
            c.drawText(label, x, y - dp(4), paint);
        }
    }

    private void drawJoystick(Canvas c) {
        paint.setColor(Color.argb(65, 255, 255, 255));
        c.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(Color.argb(135, 219, 182, 105));
        c.drawCircle(joystickCenterX, joystickCenterY, joystickRadius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(145, 70, 92, 96));
        c.drawCircle(joystickKnobX, joystickKnobY, dp(28), paint);
    }

    private void drawActionButtons(Canvas c, int w, int h) {
        float attackX = w - dp(92);
        float attackY = h - dp(95);
        float dodgeX = w - dp(205);
        float dodgeY = h - dp(67);

        drawRoundButton(c, attackX, attackY, dp(54), "АТК", Color.argb(150, 124, 67, 55));
        drawRoundButton(c, dodgeX, dodgeY, dp(43), "РЫВОК", Color.argb(145, 45, 83, 94));

        paint.setColor(Color.argb(135, 12, 17, 20));
        c.drawCircle(w - dp(45), dp(45), dp(25), paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(18));
        c.drawText("Ⅱ", w - dp(45), dp(51), paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawRoundButton(Canvas c, float cx, float cy, float radius, String text, int fill) {
        paint.setColor(fill);
        c.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(Color.argb(190, 222, 185, 109));
        c.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.WHITE);
        paint.setTextSize(dp(text.length() > 4 ? 10 : 14));
        paint.setFakeBoldText(true);
        c.drawText(text, cx, cy + dp(5), paint);
        paint.setFakeBoldText(false);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawPause(Canvas c, int w, int h) {
        paint.setColor(Color.argb(220, 5, 9, 12));
        c.drawRect(0, 0, w, h, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.rgb(218, 178, 99));
        paint.setTextSize(dp(28));
        paint.setFakeBoldText(true);
        c.drawText("ПАУЗА", w / 2f, h * 0.31f, paint);
        paint.setFakeBoldText(false);

        float bw = Math.min(dp(250), w * 0.4f);
        float bh = dp(50);
        float left = w / 2f - bw / 2f;
        float top = h * 0.43f;
        resumeRect.set(left, top, left + bw, top + bh);
        saveMenuRect.set(left, top + bh + dp(14), left + bw, top + bh * 2 + dp(14));
        drawMenuButton(c, resumeRect, "ПРОДОЛЖИТЬ", true);
        drawMenuButton(c, saveMenuRect, "СОХРАНИТЬ И В МЕНЮ", true);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawDeath(Canvas c, int w, int h) {
        paint.setColor(Color.argb(225, 22, 4, 6));
        c.drawRect(0, 0, w, h, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.rgb(210, 90, 83));
        paint.setTextSize(dp(30));
        paint.setFakeBoldText(true);
        c.drawText("ВЫ ПАЛИ", w / 2f, h * 0.40f, paint);
        paint.setFakeBoldText(false);
        float bw = Math.min(dp(250), w * 0.4f);
        float bh = dp(52);
        deathContinueRect.set(w / 2f - bw / 2f, h * 0.50f, w / 2f + bw / 2f, h * 0.50f + bh);
        drawMenuButton(c, deathContinueRect, "С ПОСЛЕДНЕГО СОХРАНЕНИЯ", true);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        final int actionIndex = event.getActionIndex();
        final int pointerId = event.getPointerId(actionIndex);
        final float x = event.getX(actionIndex);
        final float y = event.getY(actionIndex);

        if (mainMenu) {
            if (action == MotionEvent.ACTION_UP) {
                if (newGameRect.contains(x, y)) {
                    activity.playUiSound();
                    renderer.newGame();
                    mainMenu = false;
                } else if (continueRect.contains(x, y) && renderer.hasSave()) {
                    activity.playUiSound();
                    renderer.continueGame();
                    mainMenu = false;
                }
            }
            return true;
        }

        if (renderer.isDead()) {
            if (action == MotionEvent.ACTION_UP && deathContinueRect.contains(x, y)) {
                activity.playUiSound();
                renderer.continueGame();
            }
            return true;
        }

        if (pauseMenu || renderer.isPaused()) {
            if (action == MotionEvent.ACTION_UP) {
                if (resumeRect.contains(x, y)) {
                    activity.playUiSound();
                    pauseMenu = false;
                    renderer.setPaused(false);
                } else if (saveMenuRect.contains(x, y)) {
                    activity.playUiSound();
                    renderer.returnToMenu();
                    pauseMenu = false;
                    mainMenu = true;
                }
            }
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (insideCircle(x, y, getWidth() - dp(45), dp(45), dp(34))) {
                openPause();
                return true;
            }
            if (insideCircle(x, y, getWidth() - dp(92), getHeight() - dp(95), dp(68))) {
                renderer.requestAttack();
                activity.playAttackSound();
                return true;
            }
            if (insideCircle(x, y, getWidth() - dp(205), getHeight() - dp(67), dp(57))) {
                renderer.requestDodge();
                activity.playDodgeSound();
                return true;
            }
            if (x < getWidth() * 0.43f && y > getHeight() * 0.42f && joystickPointer < 0) {
                joystickPointer = pointerId;
                updateJoystick(x, y);
                return true;
            }
            if (x >= getWidth() * 0.38f && lookPointer < 0) {
                lookPointer = pointerId;
                lastLookX = x;
                lastLookY = y;
                return true;
            }
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                int id = event.getPointerId(i);
                float px = event.getX(i);
                float py = event.getY(i);
                if (id == joystickPointer) {
                    updateJoystick(px, py);
                } else if (id == lookPointer) {
                    float dx = px - lastLookX;
                    float dy = py - lastLookY;
                    renderer.lookBy(dx * 0.13f, dy * 0.10f);
                    lastLookX = px;
                    lastLookY = py;
                }
            }
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            if (pointerId == joystickPointer || action == MotionEvent.ACTION_CANCEL) {
                joystickPointer = -1;
                renderer.setMove(0f, 0f);
                joystickKnobX = joystickCenterX;
                joystickKnobY = joystickCenterY;
            }
            if (pointerId == lookPointer || action == MotionEvent.ACTION_CANCEL) {
                lookPointer = -1;
            }
        }
        return true;
    }

    private void updateJoystick(float x, float y) {
        float dx = x - joystickCenterX;
        float dy = y - joystickCenterY;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length > joystickRadius) {
            dx = dx / length * joystickRadius;
            dy = dy / length * joystickRadius;
        }
        joystickKnobX = joystickCenterX + dx;
        joystickKnobY = joystickCenterY + dy;
        renderer.setMove(dx / joystickRadius, -dy / joystickRadius);
    }

    private boolean insideCircle(float x, float y, float cx, float cy, float radius) {
        float dx = x - cx;
        float dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }

    private void openPause() {
        activity.playUiSound();
        pauseMenu = true;
        renderer.setPaused(true);
        renderer.setMove(0f, 0f);
        joystickPointer = -1;
        lookPointer = -1;
    }

    public void handleBack() {
        if (mainMenu) {
            activity.finish();
            return;
        }
        if (renderer.isDead()) {
            renderer.returnToMenu();
            mainMenu = true;
            pauseMenu = false;
            return;
        }
        if (pauseMenu || renderer.isPaused()) {
            pauseMenu = false;
            renderer.setPaused(false);
        } else {
            openPause();
        }
    }
}
