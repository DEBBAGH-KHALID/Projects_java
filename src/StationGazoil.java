import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class StationGazoil extends JFrame {

    public StationGazoil() {
        super("Simulation d'une station de gazoil");

        SimPanel sim = new SimPanel();

        JPanel controles = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 8));

        JButton btnPause = new JButton("Pause");
        btnPause.addActionListener(e -> {
            sim.togglePause();
            btnPause.setText(sim.isPaused() ? "Reprendre" : "Pause");
        });

        JButton btnVider = new JButton("Vider le réservoir");
        btnVider.addActionListener(e -> sim.viderReservoir());

        JSlider sliderFrequence = new JSlider(1, 10, 5);
        sliderFrequence.setPreferredSize(new Dimension(160, 40));
        sliderFrequence.setMajorTickSpacing(3);
        sliderFrequence.setPaintTicks(true);
        sliderFrequence.addChangeListener(e -> sim.setFrequence(sliderFrequence.getValue()));

        controles.add(btnPause);
        controles.add(btnVider);
        controles.add(new JLabel("Affluence des voitures :"));
        controles.add(sliderFrequence);

        add(sim, BorderLayout.CENTER);
        add(controles, BorderLayout.SOUTH);

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        setResizable(false);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new StationGazoil().setVisible(true));
    }

    // =====================================================================
    //                          PANNEAU DE SIMULATION
    // =====================================================================
    static class SimPanel extends JPanel {

        // ---- Dimensions de la scène ----
        static final int W = 1000, H = 470;
        static final int ROAD_TRUCK_Y = 170;   // route du camion (en haut)
        static final int ROAD_CAR_Y = 330;     // route des voitures (en bas)
        static final int ROAD_H = 70;

        // ---- Voitures ----
        static final int CAR_W = 90;
        static final int CAR_Y = 347;
        static final int GAP = 18;             // distance entre deux voitures
        static final int BAY_X = 430;          // position d'arrêt devant la pompe
        static final double CAR_SPEED = 3.0;

        // ---- Réservoir / pompe ----
        static final double CAP = 1000;        // capacité du réservoir (litres)
        static final double PUMP_RATE = 0.5;   // litres débités par "tick"
        static final double CALL_LEVEL = 1.0;  // niveau (L) en dessous duquel on appelle le camion

        // ---- Camion ----
        static final int TRUCK_Y = 185;
        static final int TRUCK_W = 190;
        static final int TRUCK_STOP_X = 650;
        static final double TRUCK_SPEED = 3.5;
        static final double TRUCK_RATE = 3.0;  // litres livrés par "tick"

        enum TruckState { NONE, ARRIVING, UNLOADING, LEAVING }

        static class Car {
            static final int ARRIVING = 0, FUELING = 1, LEAVING = 2;
            double x = -CAR_W;
            int state = ARRIVING;
            double needed, fueled;
            boolean waiting;                   // en attente car plus de gazoil
            Color color;
        }

        private static final Color[] PALETTE = {
                new Color(200, 40, 40), new Color(40, 100, 200), new Color(240, 190, 30),
                new Color(60, 160, 80), new Color(150, 60, 170), new Color(230, 230, 230),
                new Color(40, 40, 45), new Color(240, 130, 40)
        };

        // ---- État de la simulation ----
        private double tank = 600;
        private final List<Car> cars = new ArrayList<>();
        private final Random rnd = new Random();
        private int served = 0, deliveries = 0;
        private int spawnTimer = 30;
        private int frequence = 5;
        private boolean paused = false;
        private TruckState truckState = TruckState.NONE;
        private double truckX = W + 20;

        SimPanel() {
            setPreferredSize(new Dimension(W, H));
            Timer timer = new Timer(25, e -> {
                if (!paused) step();
                repaint();
            });
            timer.start();
        }

        // ---- Commandes ----
        void togglePause() { paused = !paused; }
        boolean isPaused() { return paused; }
        void viderReservoir() { tank = 0; }
        void setFrequence(int f) { frequence = f; }

        // =================================================================
        //                          LOGIQUE
        // =================================================================
        private void step() {
            // Apparition d'une nouvelle voiture
            spawnTimer--;
            if (spawnTimer <= 0) {
                boolean place = cars.isEmpty() || cars.get(cars.size() - 1).x >= GAP;
                if (place) {
                    cars.add(newCar());
                    spawnTimer = (11 - frequence) * 20 + 10 + rnd.nextInt(30);
                } else {
                    spawnTimer = 5;
                }
            }

            // Mise à jour de chaque voiture (de la plus avancée à la dernière arrivée)
            for (int i = 0; i < cars.size(); i++) {
                Car c = cars.get(i);
                switch (c.state) {
                    case Car.ARRIVING: {
                        // Cherche la voiture devant qui bloque le passage
                        Car blocker = null;
                        for (int j = i - 1; j >= 0; j--) {
                            Car o = cars.get(j);
                            if (o.state != Car.LEAVING || o.x < BAY_X + CAR_W + GAP) {
                                blocker = o;
                                break;
                            }
                        }
                        double target = BAY_X;
                        if (blocker != null) target = Math.min(BAY_X, blocker.x - CAR_W - GAP);
                        if (c.x < target) c.x = Math.min(c.x + CAR_SPEED, target);
                        if (blocker == null && c.x >= BAY_X) {
                            c.x = BAY_X;
                            c.state = Car.FUELING;
                        }
                        break;
                    }
                    case Car.FUELING: {
                        double delta = Math.min(Math.min(PUMP_RATE, tank), c.needed - c.fueled);
                        tank = Math.max(0, tank - delta);
                        c.fueled += delta;
                        if (c.needed - c.fueled <= 0.001) {
                            c.state = Car.LEAVING;
                            c.waiting = false;
                            served++;
                        } else {
                            c.waiting = tank <= 0.001;   // réservoir vide : la voiture attend
                        }
                        break;
                    }
                    case Car.LEAVING:
                        c.x += CAR_SPEED * 1.6;
                        break;
                }
            }
            cars.removeIf(c -> c.x > W + 20);

            updateTruck();
        }

        private void updateTruck() {
            switch (truckState) {
                case NONE:
                    if (tank <= CALL_LEVEL) {            // gazoil épuisé -> on appelle le camion
                        truckState = TruckState.ARRIVING;
                        truckX = W + 20;
                    }
                    break;
                case ARRIVING:
                    truckX -= TRUCK_SPEED;
                    if (truckX <= TRUCK_STOP_X) {
                        truckX = TRUCK_STOP_X;
                        truckState = TruckState.UNLOADING;
                    }
                    break;
                case UNLOADING:
                    tank = Math.min(CAP, tank + TRUCK_RATE);
                    if (tank >= CAP) {
                        deliveries++;
                        truckState = TruckState.LEAVING;
                    }
                    break;
                case LEAVING:
                    truckX -= TRUCK_SPEED;
                    if (truckX < -TRUCK_W - 20) truckState = TruckState.NONE;
                    break;
            }
        }

        private Car newCar() {
            Car c = new Car();
            c.needed = 20 + rnd.nextInt(41);             // 20 à 60 litres
            c.color = PALETTE[rnd.nextInt(PALETTE.length)];
            return c;
        }

        // =================================================================
        //                          DESSIN
        // =================================================================
        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Fond
            g.setPaint(new GradientPaint(0, 0, new Color(150, 205, 240), 0, H, new Color(190, 225, 170)));
            g.fillRect(0, 0, W, H);

            // Routes
            drawRoad(g, ROAD_TRUCK_Y);
            drawRoad(g, ROAD_CAR_Y);

            drawTank(g);
            drawPipe(g);
            drawStation(g);

            // Voitures
            for (Car c : cars) drawCar(g, c);

            // Tuyau de pompe vers la voiture en train de se servir
            for (Car c : cars) {
                if (c.state == Car.FUELING) {
                    g.setColor(Color.BLACK);
                    g.setStroke(new BasicStroke(3));
                    g.draw(new QuadCurve2D.Double(467, 292, c.x + 5, 285, c.x + 24, CAR_Y + 10));
                }
            }

            // Camion
            if (truckState != TruckState.NONE) drawTruck(g);

            drawHud(g);
            g.dispose();
        }

        private void drawRoad(Graphics2D g, int y) {
            g.setColor(new Color(70, 72, 78));
            g.fillRect(0, y, W, ROAD_H);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10,
                    new float[]{18, 16}, 0));
            g.drawLine(0, y + ROAD_H / 2, W, y + ROAD_H / 2);
        }

        private void drawTank(Graphics2D g) {
            int tx = 680, ty = 45, tw = 240, th = 105;
            RoundRectangle2D shape = new RoundRectangle2D.Double(tx, ty, tw, th, 50, 50);
            g.setColor(new Color(235, 235, 240));
            g.fill(shape);

            double ratio = tank / CAP;
            int fh = (int) (th * ratio);
            Shape old = g.getClip();
            g.setClip(shape);
            g.setColor(ratio < 0.15 ? new Color(220, 80, 60) : new Color(230, 170, 40));
            g.fillRect(tx, ty + th - fh, tw, fh);
            g.setClip(old);

            g.setColor(new Color(60, 60, 70));
            g.setStroke(new BasicStroke(3));
            g.draw(shape);

            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            drawCentered(g, "RÉSERVOIR GAZOIL", tx + tw / 2, ty + 42, new Color(30, 30, 40));
            drawCentered(g, String.format("%.0f L  (%.0f%%)", tank, ratio * 100), tx + tw / 2, ty + 66,
                    new Color(30, 30, 40));
        }

        private void drawPipe(Graphics2D g) {
            g.setColor(new Color(110, 110, 120));
            g.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Double();
            p.moveTo(700, 150);
            p.lineTo(700, 316);
            p.lineTo(490, 316);
            g.draw(p);
        }

        private void drawStation(Graphics2D g) {
            // Colonnes
            g.setColor(new Color(150, 150, 155));
            g.fillRect(418, 267, 8, 63);
            g.fillRect(548, 267, 8, 63);
            // Auvent
            g.setColor(new Color(30, 80, 170));
            g.fillRoundRect(405, 245, 165, 22, 8, 8);
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            drawCentered(g, "STATION GAZOIL", 487, 261, Color.WHITE);
            // Îlot
            g.setColor(new Color(190, 190, 195));
            g.fillRect(455, 322, 60, 8);
            // Pompe
            g.setColor(new Color(200, 50, 50));
            g.fillRoundRect(470, 272, 30, 50, 6, 6);
            g.setColor(new Color(30, 60, 40));
            g.fillRect(475, 278, 20, 12);
            g.setColor(new Color(120, 255, 140));
            g.setFont(new Font("Monospaced", Font.BOLD, 10));
            g.drawString("GO", 478, 288);
            g.setColor(Color.DARK_GRAY);
            g.fillRect(464, 290, 6, 20);
        }

        private void drawCar(Graphics2D g, Car c) {
            int x = (int) c.x, y = CAR_Y;
            g.setColor(c.color);
            g.fillRoundRect(x, y + 10, CAR_W, 26, 10, 10);
            g.fillRoundRect(x + 18, y, 50, 18, 12, 12);
            g.setColor(new Color(190, 225, 245));
            g.fillRoundRect(x + 23, y + 4, 18, 12, 4, 4);
            g.fillRoundRect(x + 45, y + 4, 18, 12, 4, 4);
            g.setColor(new Color(255, 240, 150));
            g.fillRect(x + CAR_W - 6, y + 16, 6, 6);
            g.setColor(new Color(220, 40, 40));
            g.fillRect(x, y + 16, 4, 6);
            // Roues
            g.setColor(Color.BLACK);
            g.fillOval(x + 14, y + 26, 18, 18);
            g.fillOval(x + CAR_W - 32, y + 26, 18, 18);
            g.setColor(Color.LIGHT_GRAY);
            g.fillOval(x + 19, y + 31, 8, 8);
            g.fillOval(x + CAR_W - 27, y + 31, 8, 8);

            if (c.waiting) {
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                drawCentered(g, "Plus de gazoil...", x + CAR_W / 2, y - 6, new Color(200, 30, 30));
            } else if (c.state == Car.FUELING) {
                g.setFont(new Font("SansSerif", Font.BOLD, 12));
                drawCentered(g, String.format("%.0f / %.0f L", c.fueled, c.needed), x + CAR_W / 2, y - 6,
                        new Color(20, 20, 20));
            }
        }

        private void drawTruck(Graphics2D g) {
            int x = (int) truckX, y = TRUCK_Y;

            // Tuyau de déchargement
            if (truckState == TruckState.UNLOADING) {
                g.setColor(new Color(30, 90, 40));
                g.setStroke(new BasicStroke(5, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x + 125, y, x + 125, 150);
            }

            // Citerne (à droite) et cabine (à gauche) : le camion roule vers la gauche
            g.setColor(new Color(215, 215, 220));
            g.fillRoundRect(x + 60, y, 130, 40, 16, 16);
            g.setColor(new Color(60, 60, 70));
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x + 60, y, 130, 40, 16, 16);
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            drawCentered(g, "GAZOIL", x + 125, y + 25, new Color(200, 40, 40));

            g.setColor(new Color(200, 50, 50));
            g.fillRoundRect(x, y + 12, 60, 28, 8, 8);
            g.setColor(new Color(190, 225, 245));
            g.fillRect(x + 6, y + 16, 18, 12);

            g.setColor(Color.BLACK);
            g.fillOval(x + 12, y + 32, 18, 18);
            g.fillOval(x + 85, y + 32, 18, 18);
            g.fillOval(x + 150, y + 32, 18, 18);
            g.setColor(Color.LIGHT_GRAY);
            g.fillOval(x + 17, y + 37, 8, 8);
            g.fillOval(x + 90, y + 37, 8, 8);
            g.fillOval(x + 155, y + 37, 8, 8);
        }

        private void drawHud(Graphics2D g) {
            g.setColor(new Color(255, 255, 255, 215));
            g.fillRoundRect(15, 15, 340, 125, 14, 14);
            g.setColor(new Color(60, 60, 70));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(15, 15, 340, 125, 14, 14);

            g.setColor(new Color(30, 30, 40));
            g.setFont(new Font("SansSerif", Font.BOLD, 15));
            g.drawString("Station gazoil - Tableau de bord", 28, 38);

            g.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g.drawString(String.format("Niveau : %.0f / %.0f L", tank, CAP), 28, 58);

            // Barre de niveau
            double ratio = tank / CAP;
            g.setColor(new Color(220, 220, 225));
            g.fillRoundRect(28, 65, 310, 14, 8, 8);
            g.setColor(ratio < 0.15 ? new Color(220, 80, 60) : new Color(230, 170, 40));
            g.fillRoundRect(28, 65, (int) (310 * ratio), 14, 8, 8);
            g.setColor(new Color(60, 60, 70));
            g.drawRoundRect(28, 65, 310, 14, 8, 8);

            g.setColor(new Color(30, 30, 40));
            int queue = 0;
            for (Car c : cars) if (c.state == Car.ARRIVING) queue++;
            g.drawString("Voitures servies : " + served + "     File d'attente : " + queue, 28, 100);
            g.drawString("Camions livrés : " + deliveries, 28, 118);

            String statut;
            Color col = new Color(30, 30, 40);
            switch (truckState) {
                case ARRIVING:
                    statut = "Camion en route vers la station...";
                    col = new Color(200, 100, 0);
                    break;
                case UNLOADING:
                    statut = "Déchargement du camion en cours...";
                    col = new Color(30, 130, 50);
                    break;
                case LEAVING:
                    statut = "Réservoir plein : le camion repart.";
                    col = new Color(30, 130, 50);
                    break;
                default:
                    statut = tank <= CALL_LEVEL ? "Gazoil épuisé !" : "Camion : en attente";
                    if (tank <= CALL_LEVEL) col = new Color(200, 30, 30);
            }
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(col);
            g.drawString(statut, 28, 135);
        }

        private void drawCentered(Graphics2D g, String s, int cx, int baseY, Color color) {
            FontMetrics fm = g.getFontMetrics();
            g.setColor(color);
            g.drawString(s, cx - fm.stringWidth(s) / 2, baseY);
        }
    }
}