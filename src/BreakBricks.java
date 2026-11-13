import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class BreakBricks {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Break Bricks");
            Board board = new Board();
            frame.add(board);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            board.requestFocusInWindow();
            board.start();
        });
    }
}

/** Une balle : position et vecteur vitesse. */
class Ball {
    double x, y, dx, dy;
    Ball(double x, double y) { this.x = x; this.y = y; }
}

class Board extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener {

    // ---------- Constantes ----------
    static final int W = 480, H = 640;
    static final int COLS = 8, ROWS = 6;
    static final int BW = 50, BH = 22, GAP = 6, TOP = 70;
    static final int LEFT = (W - (COLS * BW + (COLS - 1) * GAP)) / 2;
    static final int PW = 84, PH = 14, PY = H - 46;
    static final int R = 8;
    static final int MAX_LEVEL = 5;

    // Reglages de vitesse et de multi-balles
    static final double SPEED_START = 5.0;   // vitesse de depart
    static final double SPEED_MIN = 2.0;
    static final double SPEED_MAX = 12.0;
    static final double SPEED_STEP = 0.5;    // pas de changement avec haut/bas
    static final double LEVEL_BOOST = 0.6;   // gain de vitesse a chaque niveau
    static final int MAX_BALLS = 50;

    static final Color BG = new Color(24, 29, 56);
    static final Color INK = new Color(232, 235, 255);
    static final Color BALL = new Color(255, 209, 102);
    static final Color[] ROW_COLORS = {
            new Color(0xE4572E), new Color(0xF3A712), new Color(0xF6D743),
            new Color(0x59C36A), new Color(0x2FA4D6), new Color(0x7B61E0)
    };

    enum State { READY, PLAY, PAUSED, OVER, WIN }

    // ---------- Thread ----------
    private Thread loop;
    private volatile boolean running = false;
    private final Object lock = new Object();

    // ---------- Etat ----------
    private State state;
    private int score, lives, level, left;
    private double speed;
    private final boolean[][] bricks = new boolean[ROWS][COLS];
    private final List<Ball> balls = new ArrayList<>();
    private double px;
    private volatile boolean keyL = false, keyR = false;

    Board() {
        setPreferredSize(new Dimension(W, H));
        setBackground(BG);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        newGame();
    }

    // ---------- Thread de jeu ----------
    void start() {
        if (running) return;
        running = true;
        loop = new Thread(this, "GameLoop");
        loop.start();
    }

    void stop() {
        running = false;
        try { if (loop != null) loop.join(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            double dt = Math.min((now - last) / 16_666_667.0, 2.0); // 1.0 = une image a 60 FPS
            last = now;

            synchronized (lock) { update(dt); }
            repaint();

            try { Thread.sleep(8); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
    }

    // ---------- Logique ----------
    private void newGame() {
        synchronized (lock) {
            score = 0; lives = 3; level = 1; speed = SPEED_START;
            buildBricks();
            resetBall();
        }
    }

    private void buildBricks() {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                bricks[r][c] = true;
        left = ROWS * COLS;
    }

    /** Remet une seule balle collee a la raquette. */
    private void resetBall() {
        px = (W - PW) / 2.0;
        balls.clear();
        balls.add(new Ball(W / 2.0, PY - R - 1));
        state = State.READY;
    }

    private void launch() {
        Ball b = balls.get(0);
        double a = Math.toRadians(Math.random() * 60 - 30);
        b.dx = speed * Math.sin(a);
        b.dy = -speed * Math.cos(a);
        state = State.PLAY;
    }

    // ----- Vitesse -----
    /** Change la vitesse globale et met a l'echelle toutes les balles deja en jeu. */
    private void setSpeed(double newSpeed) {
        speed = Math.max(SPEED_MIN, Math.min(SPEED_MAX, newSpeed));
        for (Ball b : balls) {
            double mag = Math.hypot(b.dx, b.dy);
            if (mag > 0) {
                b.dx = b.dx / mag * speed;
                b.dy = b.dy / mag * speed;
            }
        }
    }

    private void changeSpeed(double delta) {
        synchronized (lock) { setSpeed(speed + delta); }
    }

    // ----- Multi-balles -----
    private void addBall() {
        synchronized (lock) {
            if (state != State.PLAY || balls.size() >= MAX_BALLS) return;
            Ball b = new Ball(px + PW / 2.0, PY - R - 1);
            double a = Math.toRadians(Math.random() * 80 - 40);
            b.dx = speed * Math.sin(a);
            b.dy = -speed * Math.cos(a);
            balls.add(b);
        }
    }

    private void action() {
        synchronized (lock) {
            if (state == State.READY) launch();
            else if (state == State.OVER || state == State.WIN) newGame();
        }
    }

    private void togglePause() {
        synchronized (lock) {
            if (state == State.PLAY) state = State.PAUSED;
            else if (state == State.PAUSED) state = State.PLAY;
        }
    }

    private void update(double dt) {
        if (keyL) px -= 8 * dt;
        if (keyR) px += 8 * dt;
        px = Math.max(0, Math.min(W - PW, px));

        if (state == State.READY) {
            Ball b = balls.get(0);
            b.x = px + PW / 2.0;
            b.y = PY - R - 1;
            return;
        }
        if (state != State.PLAY) return;

        for (Iterator<Ball> it = balls.iterator(); it.hasNext(); ) {
            Ball b = it.next();
            b.x += b.dx * dt;
            b.y += b.dy * dt;

            // Murs
            if (b.x - R < 0) { b.x = R; b.dx = Math.abs(b.dx); }
            if (b.x + R > W) { b.x = W - R; b.dx = -Math.abs(b.dx); }
            if (b.y - R < 0) { b.y = R; b.dy = Math.abs(b.dy); }

            // Raquette : l'angle depend de l'endroit touche
            if (b.dy > 0 && b.y + R >= PY && b.y - R <= PY + PH
                    && b.x >= px - R && b.x <= px + PW + R) {
                double h = Math.max(-1, Math.min(1, (b.x - (px + PW / 2.0)) / (PW / 2.0)));
                double a = h * Math.PI / 3;
                b.dx = speed * Math.sin(a);
                b.dy = -speed * Math.cos(a);
                b.y = PY - R;
            }

            brickHit(b);

            // Balle perdue : on la retire seulement
            if (b.y - R > H) it.remove();
        }

        // Plus aucune balle : une vie en moins
        if (balls.isEmpty()) {
            lives--;
            if (lives <= 0) {
                state = State.OVER;
                balls.add(new Ball(-100, -100)); // balle invisible pour garder la liste non vide
            } else {
                resetBall();
            }
            return;
        }

        // Niveau termine
        if (left == 0) {
            level++;
            if (level > MAX_LEVEL) {
                level = MAX_LEVEL;
                state = State.WIN;
            } else {
                speed = Math.min(SPEED_MAX, speed + LEVEL_BOOST);
                buildBricks();
                resetBall();
            }
        }
    }

    private void brickHit(Ball b) {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (!bricks[r][c]) continue;
                int x = LEFT + c * (BW + GAP);
                int y = TOP + r * (BH + GAP);
                if (b.x + R > x && b.x - R < x + BW && b.y + R > y && b.y - R < y + BH) {
                    bricks[r][c] = false;
                    left--;
                    score += 10 * (ROWS - r);
                    double mx = Math.min(b.x + R - x, x + BW - (b.x - R));
                    double my = Math.min(b.y + R - y, y + BH - (b.y - R));
                    if (mx < my) b.dx = -b.dx; else b.dy = -b.dy;
                    return; // une brique par balle et par mise a jour
                }
            }
        }
    }

    // ---------- Rendu (EDT) ----------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        synchronized (lock) {
            // Barre d'infos
            g2.setColor(INK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2.drawString("Score  " + score, 20, 28);
            drawCentered(g2, "Level  " + level, W / 2, 28);
            String lv = "Lives  " + lives;
            g2.drawString(lv, W - 20 - g2.getFontMetrics().stringWidth(lv), 28);

            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.drawString(String.format("Speed  %.1f   (Up / Down)", speed), 20, 50);
            String nb = "Balls  " + balls.size() + "/" + MAX_BALLS + "   (B)";
            if (state == State.OVER) nb = "Balls  0/" + MAX_BALLS + "   (B)";
            g2.drawString(nb, W - 20 - g2.getFontMetrics().stringWidth(nb), 50);

            // Briques
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (!bricks[r][c]) continue;
                    g2.setColor(ROW_COLORS[r % ROW_COLORS.length]);
                    g2.fillRoundRect(LEFT + c * (BW + GAP), TOP + r * (BH + GAP), BW, BH, 10, 10);
                }
            }

            // Raquette et balles
            g2.setColor(INK);
            g2.fillRoundRect((int) px, PY, PW, PH, 14, 14);
            g2.setColor(BALL);
            for (Ball b : balls)
                g2.fillOval((int) (b.x - R), (int) (b.y - R), R * 2, R * 2);

            // Messages
            switch (state) {
                case READY -> banner(g2, "Ready", "Press Space or click to launch");
                case PAUSED -> banner(g2, "Paused", "Press P to continue");
                case OVER -> banner(g2, "Game over", "Score " + score + ". Press Space to play again");
                case WIN -> banner(g2, "You cleared every level", "Score " + score + ". Press Space to play again");
                default -> { }
            }
        }
        Toolkit.getDefaultToolkit().sync();
    }

    private void drawCentered(Graphics2D g2, String text, int cx, int y) {
        g2.drawString(text, cx - g2.getFontMetrics().stringWidth(text) / 2, y);
    }

    private void banner(Graphics2D g2, String title, String sub) {
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRect(0, H / 2 - 50, W, 100);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 30));
        drawCentered(g2, title, W / 2, H / 2 - 2);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 16));
        drawCentered(g2, sub, W / 2, H / 2 + 28);
    }

    // ---------- Clavier ----------
    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) keyL = true;
        else if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) keyR = true;
        else if (k == KeyEvent.VK_SPACE) action();
        else if (k == KeyEvent.VK_P) togglePause();
        else if (k == KeyEvent.VK_B) addBall();
        else if (k == KeyEvent.VK_UP || k == KeyEvent.VK_PLUS
                || k == KeyEvent.VK_EQUALS || k == KeyEvent.VK_ADD) changeSpeed(SPEED_STEP);
        else if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_MINUS
                || k == KeyEvent.VK_SUBTRACT) changeSpeed(-SPEED_STEP);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT || k == KeyEvent.VK_A) keyL = false;
        if (k == KeyEvent.VK_RIGHT || k == KeyEvent.VK_D) keyR = false;
    }

    @Override public void keyTyped(KeyEvent e) { }

    // ---------- Souris ----------
    private void movePaddle(MouseEvent e) {
        synchronized (lock) {
            if (state != State.PAUSED)
                px = Math.max(0, Math.min(W - PW, e.getX() - PW / 2.0));
        }
    }

    @Override public void mouseMoved(MouseEvent e) { movePaddle(e); }
    @Override public void mouseDragged(MouseEvent e) { movePaddle(e); }
    @Override public void mousePressed(MouseEvent e) { requestFocusInWindow(); movePaddle(e); action(); }
    @Override public void mouseClicked(MouseEvent e) { }
    @Override public void mouseReleased(MouseEvent e) { }
    @Override public void mouseEntered(MouseEvent e) { }
    @Override public void mouseExited(MouseEvent e) { }
}