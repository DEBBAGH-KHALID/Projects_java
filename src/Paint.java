import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Clone de Microsoft projects.Paint en Java Swing (menus, layouts, événements, dialogues,
 *  dessin 2D, copier/coller, glisser-déposer, langues FR/EN/ES). */
public class Paint extends JFrame {
    enum Tool { PENCIL, BRUSH, ERASER, BUCKET, PICK, LINE, RECT, OVAL, ROUND, TEXT, SELECT }

    // ---------- Langues : clé=FR|EN|ES ----------
    static final String DATA =
            "file=Fichier|File|Archivo;new=Nouveau|New|Nuevo;open=Ouvrir...|Open...|Abrir...;save=Enregistrer|Save|Guardar;"
                    + "saveas=Enregistrer sous...|Save as...|Guardar como...;exit=Quitter|Exit|Salir;edit=Édition|Edit|Edición;"
                    + "undo=Annuler|Undo|Deshacer;redo=Rétablir|Redo|Rehacer;cut=Couper|Cut|Cortar;copy=Copier|Copy|Copiar;"
                    + "paste=Coller|Paste|Pegar;selall=Tout sélectionner|Select all|Seleccionar todo;view=Affichage|View|Ver;"
                    + "zin=Zoom avant|Zoom in|Acercar;zout=Zoom arrière|Zoom out|Alejar;z100=Zoom 100 %|Zoom 100 %|Zoom 100 %;"
                    + "image=Image|Image|Imagen;resize=Redimensionner la zone...|Resize canvas...|Cambiar tamaño...;"
                    + "clear=Effacer l'image|Clear image|Borrar imagen;fh=Retourner horizontalement|Flip horizontal|Voltear horizontal;"
                    + "fv=Retourner verticalement|Flip vertical|Voltear vertical;rot=Pivoter de 90°|Rotate 90°|Girar 90°;"
                    + "lang=Langue|Language|Idioma;help=Aide|Help|Ayuda;about=À propos|About|Acerca de;size=Taille|Size|Tamaño;"
                    + "fill=Remplir la forme|Fill shape|Rellenar forma;more=Plus de couleurs...|More colors...|Más colores...;"
                    + "unsaved=Enregistrer les modifications ?|Save changes?|¿Guardar cambios?;width=Largeur|Width|Ancho;"
                    + "height=Hauteur|Height|Alto;textq=Texte :|Text:|Texto:;untitled=Sans titre|Untitled|Sin título;"
                    + "aboutmsg=Clone de projects.Paint en Java Swing|projects.Paint clone in Java Swing|Clon de projects.Paint en Java Swing;"
                    + "pencil=Crayon|Pencil|Lápiz;brush=Pinceau|Brush|Pincel;eraser=Gomme|Eraser|Goma;bucket=Pot de peinture|Fill|Bote;"
                    + "pick=Pipette|Picker|Cuentagotas;line=Ligne|Line|Línea;rect=Rectangle|Rectangle|Rectángulo;"
                    + "oval=Ellipse|Ellipse|Elipse;round=Rect. arrondi|Rounded|Redondeado;text=Texte|Text|Texto;"
                    + "select=Sélection|Select|Selección;error=Erreur|Error|Error";
    static final Map<String, String[]> M = new HashMap<>();
    static { for (String e : DATA.split(";")) { String[] kv = e.split("=", 2); M.put(kv[0], kv[1].split("\\|")); } }

    static final Color[] PAL = { Color.BLACK, new Color(0x7F7F7F), new Color(0x880015), new Color(0xED1C24),
            new Color(0xFF7F27), new Color(0xFFF200), new Color(0x22B14C), new Color(0x00A2E8), new Color(0x3F48CC),
            new Color(0xA349A4), Color.WHITE, new Color(0xC3C3C3), new Color(0xB97A57), new Color(0xFFAEC9),
            new Color(0xFFC90E), new Color(0xEFE4B0), new Color(0xB5E61D), new Color(0x99D9EA), new Color(0x7092BE),
            new Color(0xC8BFE7) };

    int lang = 0;
    Color fg = Color.BLACK, bg = Color.WHITE;
    File file;
    final EnumMap<Tool, JToggleButton> tb = new EnumMap<>(Tool.class);
    final JSlider sizeSl = new JSlider(1, 40, 3);
    final JCheckBox fillBox = new JCheckBox();
    final JLabel status = new JLabel(" ");
    final JPanel indicator = new JPanel() {
        { setPreferredSize(new Dimension(48, 48)); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(bg); g.fillRect(16, 16, 28, 28); g.setColor(Color.DARK_GRAY); g.drawRect(16, 16, 28, 28);
            g.setColor(fg); g.fillRect(4, 4, 28, 28); g.setColor(Color.DARK_GRAY); g.drawRect(4, 4, 28, 28);
        }
    };
    final JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
    final Canvas cv = new Canvas();

    String t(String k) { String[] v = M.get(k); return v == null ? k : v[lang]; }

    static BufferedImage blank(int w, int h) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, w, h); g.dispose();
        return b;
    }
    static BufferedImage dup(Image s) {
        BufferedImage b = new BufferedImage(s.getWidth(null), s.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = b.createGraphics(); g.drawImage(s, 0, 0, null); g.dispose();
        return b;
    }

    // ---------- Fenêtre ----------
    Paint() {
        super("projects.Paint");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { if (confirm()) System.exit(0); }
        });
        wrapper.setBackground(new Color(0x6E7F96));
        wrapper.add(cv);
        DropHandler dh = new DropHandler(wrapper);
        wrapper.setTransferHandler(dh);
        cv.setTransferHandler(new DropHandler(cv));
        buildUI();
        updateTitle();
        setSize(1100, 760);
        setLocationRelativeTo(null);
    }

    void buildUI() {
        Container c = getContentPane(); c.removeAll(); c.setLayout(new BorderLayout());
        setJMenuBar(buildMenu());

        JPanel tools = new JPanel(new GridLayout(0, 1, 2, 2)); ButtonGroup grp = new ButtonGroup(); tb.clear();
        for (Tool tl : Tool.values()) {
            JToggleButton b = new JToggleButton(t(tl.name().toLowerCase()), cv.tool == tl);
            b.addActionListener(e -> cv.tool = tl);
            grp.add(b); tools.add(b); tb.put(tl, b);
        }
        JPanel opt = new JPanel(new GridLayout(0, 1));
        opt.setBorder(new TitledBorder(t("size")));
        fillBox.setText(t("fill")); opt.add(sizeSl); opt.add(fillBox);
        JPanel left = new JPanel(); left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        left.add(tools); left.add(Box.createVerticalStrut(8)); left.add(opt);
        c.add(left, BorderLayout.WEST);

        JPanel pal = new JPanel(new GridLayout(2, 10, 2, 2));
        for (Color col : PAL) {
            JPanel s = new JPanel(); s.setBackground(col); s.setPreferredSize(new Dimension(22, 22));
            s.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            s.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) bg = col; else fg = col;
                    indicator.repaint();
                }
            });
            pal.add(s);
        }
        JButton more = new JButton(t("more"));
        more.addActionListener(e -> {
            Color n = JColorChooser.showDialog(this, t("more"), fg);
            if (n != null) { fg = n; indicator.repaint(); }
        });
        JPanel palBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        palBar.add(indicator); palBar.add(pal); palBar.add(more);
        JPanel south = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        south.add(palBar, BorderLayout.NORTH); south.add(status, BorderLayout.SOUTH);
        c.add(south, BorderLayout.SOUTH);

        JScrollPane sp = new JScrollPane(wrapper);
        sp.getVerticalScrollBar().setUnitIncrement(20);
        c.add(sp, BorderLayout.CENTER);
        cv.moved(new Point(0, 0));
        c.revalidate(); c.repaint();
    }

    JMenuItem mi(String k, int key, Runnable r) {
        JMenuItem m = new JMenuItem(t(k));
        if (key != 0) m.setAccelerator(KeyStroke.getKeyStroke(key, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        m.addActionListener(e -> r.run());
        return m;
    }

    JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu f = new JMenu(t("file"));
        f.add(mi("new", KeyEvent.VK_N, this::newImage)); f.add(mi("open", KeyEvent.VK_O, this::open));
        f.add(mi("save", KeyEvent.VK_S, () -> save(false))); f.add(mi("saveas", 0, () -> save(true)));
        f.addSeparator(); f.add(mi("exit", 0, () -> { if (confirm()) System.exit(0); }));
        JMenu ed = new JMenu(t("edit"));
        ed.add(mi("undo", KeyEvent.VK_Z, cv::undo)); ed.add(mi("redo", KeyEvent.VK_Y, cv::redo)); ed.addSeparator();
        ed.add(mi("cut", KeyEvent.VK_X, cv::cut)); ed.add(mi("copy", KeyEvent.VK_C, cv::copy));
        ed.add(mi("paste", KeyEvent.VK_V, cv::paste)); ed.add(mi("selall", KeyEvent.VK_A, cv::selectAll));
        JMenu vw = new JMenu(t("view"));
        vw.add(mi("zin", KeyEvent.VK_ADD, () -> cv.setZoom(cv.zoom * 1.25)));
        vw.add(mi("zout", KeyEvent.VK_SUBTRACT, () -> cv.setZoom(cv.zoom / 1.25)));
        vw.add(mi("z100", KeyEvent.VK_0, () -> cv.setZoom(1)));
        JMenu im = new JMenu(t("image"));
        im.add(mi("resize", 0, this::resizeDialog)); im.add(mi("clear", 0, cv::clearImage)); im.addSeparator();
        im.add(mi("fh", 0, () -> cv.flip(true))); im.add(mi("fv", 0, () -> cv.flip(false)));
        im.add(mi("rot", 0, cv::rotate));
        JMenu lg = new JMenu(t("lang")); ButtonGroup g = new ButtonGroup();
        String[] names = { "Français", "English", "Español" };
        for (int i = 0; i < 3; i++) {
            int li = i; JRadioButtonMenuItem r = new JRadioButtonMenuItem(names[i], lang == i);
            r.addActionListener(e -> { lang = li; buildUI(); updateTitle(); });
            g.add(r); lg.add(r);
        }
        JMenu hp = new JMenu(t("help"));
        hp.add(mi("about", 0, () -> JOptionPane.showMessageDialog(this, t("aboutmsg"), t("about"), JOptionPane.INFORMATION_MESSAGE)));
        for (JMenu m : new JMenu[] { f, ed, vw, im, lg, hp }) bar.add(m);
        return bar;
    }

    void selectTool(Tool t) { cv.tool = t; JToggleButton b = tb.get(t); if (b != null) b.setSelected(true); }

    void updateTitle() { setTitle((cv.dirty ? "*" : "") + (file == null ? t("untitled") : file.getName()) + " - projects.Paint"); }

    // ---------- Fichiers ----------
    boolean confirm() {
        if (!cv.dirty) return true;
        int r = JOptionPane.showConfirmDialog(this, t("unsaved"), "projects.Paint", JOptionPane.YES_NO_CANCEL_OPTION);
        if (r == JOptionPane.CANCEL_OPTION || r == JOptionPane.CLOSED_OPTION) return false;
        return r != JOptionPane.YES_OPTION || save(false);
    }

    void newImage() {
        if (!confirm()) return;
        file = null; cv.undoSt.clear(); cv.redoSt.clear(); cv.setImage(blank(800, 500)); cv.dirty = false; updateTitle();
    }

    void open() {
        if (!confirm()) return;
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Images (png, jpg, bmp, gif)", "png", "jpg", "jpeg", "bmp", "gif"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage b = ImageIO.read(fc.getSelectedFile());
            if (b == null) throw new IOException("?");
            file = fc.getSelectedFile(); cv.undoSt.clear(); cv.redoSt.clear();
            cv.setImage(dup(b)); cv.dirty = false; updateTitle();
        } catch (IOException ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), t("error"), JOptionPane.ERROR_MESSAGE); }
    }

    boolean save(boolean as) {
        cv.commit();
        if (file == null || as) {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("PNG, JPG, BMP", "png", "jpg", "jpeg", "bmp"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
            file = fc.getSelectedFile();
            if (!file.getName().contains(".")) file = new File(file.getPath() + ".png");
        }
        String n = file.getName().toLowerCase();
        String fmt = n.endsWith(".jpg") || n.endsWith(".jpeg") ? "jpg" : n.endsWith(".bmp") ? "bmp" : "png";
        BufferedImage o = cv.img;
        if (!fmt.equals("png")) {
            o = new BufferedImage(o.getWidth(), o.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = o.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, o.getWidth(), o.getHeight());
            g.drawImage(cv.img, 0, 0, null); g.dispose();
        }
        try { ImageIO.write(o, fmt, file); cv.dirty = false; updateTitle(); return true; }
        catch (IOException ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), t("error"), JOptionPane.ERROR_MESSAGE); return false; }
    }

    void resizeDialog() {
        JSpinner w = new JSpinner(new SpinnerNumberModel(cv.img.getWidth(), 1, 10000, 10));
        JSpinner h = new JSpinner(new SpinnerNumberModel(cv.img.getHeight(), 1, 10000, 10));
        JPanel p = new JPanel(new GridLayout(2, 2, 6, 6));
        p.add(new JLabel(t("width"))); p.add(w); p.add(new JLabel(t("height"))); p.add(h);
        if (JOptionPane.showConfirmDialog(this, p, t("resize"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        cv.commit(); cv.push();
        BufferedImage n = blank((Integer) w.getValue(), (Integer) h.getValue());
        Graphics2D g = n.createGraphics(); g.drawImage(cv.img, 0, 0, null); g.dispose();
        cv.setImage(n);
    }

    // ---------- Glisser-déposer ----------
    class DropHandler extends TransferHandler {
        final Component owner;
        DropHandler(Component o) { owner = o; }
        @Override public boolean canImport(TransferSupport s) {
            return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || s.isDataFlavorSupported(DataFlavor.imageFlavor);
        }
        @Override @SuppressWarnings("unchecked") public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
                Transferable tr = s.getTransferable(); Image im;
                if (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                    im = ImageIO.read(((java.util.List<File>) tr.getTransferData(DataFlavor.javaFileListFlavor)).get(0));
                else im = (Image) tr.getTransferData(DataFlavor.imageFlavor);
                if (im == null) return false;
                Point d = s.isDrop() ? cv.toImg(SwingUtilities.convertPoint(owner, s.getDropLocation().getDropPoint(), cv)) : new Point(0, 0);
                cv.setFloat(dup(im), Math.max(0, d.x), Math.max(0, d.y));
                return true;
            } catch (Exception ex) { return false; }
        }
    }

    // ---------- Zone de dessin ----------
    class Canvas extends JPanel {
        BufferedImage img = blank(800, 500);
        final ArrayDeque<BufferedImage> undoSt = new ArrayDeque<>(), redoSt = new ArrayDeque<>();
        Tool tool = Tool.PENCIL; double zoom = 1; boolean dirty, selecting, pushed;
        Point p0, p1, last, dragOff; Color col, alt; Rectangle sel; BufferedImage fl; int fx, fy;

        Canvas() {
            setFocusable(true); setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { press(e); }
                @Override public void mouseDragged(MouseEvent e) { drag(e); }
                @Override public void mouseReleased(MouseEvent e) { release(e); }
                @Override public void mouseMoved(MouseEvent e) { moved(toImg(e.getPoint())); }
            };
            addMouseListener(m); addMouseMotionListener(m);
            resizeView();
        }

        Point toImg(Point p) { return new Point((int) Math.floor(p.x / zoom), (int) Math.floor(p.y / zoom)); }
        boolean inside(Point p) { return p.x >= 0 && p.y >= 0 && p.x < img.getWidth() && p.y < img.getHeight(); }
        boolean free() { return tool == Tool.PENCIL || tool == Tool.BRUSH || tool == Tool.ERASER; }
        Rectangle norm(Point a, Point b) { return new Rectangle(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(a.x - b.x), Math.abs(a.y - b.y)); }
        void moved(Point p) {
            status.setText(p.x + ", " + p.y + " px     |     " + img.getWidth() + " × " + img.getHeight() + "     |     " + Math.round(zoom * 100) + " %");
        }

        void resizeView() {
            setPreferredSize(new Dimension((int) (img.getWidth() * zoom), (int) (img.getHeight() * zoom)));
            revalidate(); wrapper.revalidate(); repaint();
        }
        void setZoom(double z) { zoom = Math.max(0.25, Math.min(8, z)); resizeView(); moved(new Point(0, 0)); }
        void setImage(BufferedImage b) { img = b; sel = null; fl = null; resizeView(); moved(new Point(0, 0)); }

        // --- annuler / rétablir ---
        void push() {
            undoSt.push(dup(img)); if (undoSt.size() > 50) undoSt.removeLast();
            redoSt.clear(); dirty = true; updateTitle();
        }
        void undo() {
            fl = null; sel = null; if (undoSt.isEmpty()) return;
            redoSt.push(dup(img)); img = undoSt.pop(); dirty = true; updateTitle(); resizeView();
        }
        void redo() {
            if (redoSt.isEmpty()) return;
            undoSt.push(dup(img)); img = redoSt.pop(); dirty = true; updateTitle(); resizeView();
        }

        // --- sélection / presse-papiers ---
        void commit() {
            if (fl == null) return;
            if (!pushed) push();
            Graphics2D g = img.createGraphics(); g.drawImage(fl, fx, fy, null); g.dispose();
            fl = null; sel = null; pushed = false; repaint();
        }
        void setFloat(BufferedImage b, int x, int y) {
            commit(); fl = b; fx = x; fy = y; sel = null; pushed = false; selectTool(Tool.SELECT); repaint();
        }
        void selectAll() { commit(); selectTool(Tool.SELECT); sel = new Rectangle(0, 0, img.getWidth(), img.getHeight()); repaint(); }
        void fillRect(Rectangle r) { Graphics2D g = img.createGraphics(); g.setColor(bg); g.fillRect(r.x, r.y, r.width, r.height); g.dispose(); }

        void copy() {
            Rectangle r = sel != null ? sel : new Rectangle(0, 0, img.getWidth(), img.getHeight());
            BufferedImage s = fl != null ? fl : dup(img.getSubimage(r.x, r.y, r.width, r.height));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
                public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] { DataFlavor.imageFlavor }; }
                public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
                public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                    if (!isDataFlavorSupported(f)) throw new UnsupportedFlavorException(f);
                    return s;
                }
            }, null);
        }
        void cut() {
            if (fl == null && sel == null) return;
            copy();
            if (fl != null) { fl = null; if (!pushed) { } pushed = false; }
            else { push(); fillRect(sel); sel = null; }
            repaint();
        }
        void paste() {
            try {
                Transferable tr = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                if (tr != null && tr.isDataFlavorSupported(DataFlavor.imageFlavor))
                    setFloat(dup((Image) tr.getTransferData(DataFlavor.imageFlavor)), 0, 0);
            } catch (Exception ex) { /* presse-papiers vide ou illisible */ }
        }

        // --- transformations d'image ---
        void clearImage() { commit(); push(); fillRect(new Rectangle(0, 0, img.getWidth(), img.getHeight())); repaint(); }
        void flip(boolean horiz) {
            commit(); push(); int w = img.getWidth(), h = img.getHeight();
            BufferedImage n = blank(w, h); Graphics2D g = n.createGraphics();
            if (horiz) g.drawImage(img, w, 0, 0, h, 0, 0, w, h, null); else g.drawImage(img, 0, h, w, 0, 0, 0, w, h, null);
            g.dispose(); img = n; repaint();
        }
        void rotate() {
            commit(); push(); int w = img.getWidth(), h = img.getHeight();
            BufferedImage n = blank(h, w); Graphics2D g = n.createGraphics();
            g.translate(h, 0); g.rotate(Math.PI / 2); g.drawImage(img, 0, 0, null); g.dispose();
            img = n; resizeView();
        }

        // --- souris ---
        void press(MouseEvent e) {
            requestFocusInWindow(); Point p = toImg(e.getPoint());
            boolean right = SwingUtilities.isRightMouseButton(e);
            col = right ? bg : fg; alt = right ? fg : bg;
            if (tool == Tool.SELECT) {
                if (fl == null && sel != null && sel.contains(p)) { // "soulever" la sélection
                    push(); pushed = true; fl = dup(img.getSubimage(sel.x, sel.y, sel.width, sel.height));
                    fillRect(sel); fx = sel.x; fy = sel.y;
                }
                if (fl != null && new Rectangle(fx, fy, fl.getWidth(), fl.getHeight()).contains(p)) {
                    dragOff = new Point(p.x - fx, p.y - fy); return;
                }
                commit(); sel = null; p0 = p1 = p; selecting = true; repaint(); return;
            }
            commit();
            if ((tool == Tool.BUCKET || tool == Tool.PICK) && !inside(p)) return;
            if (tool == Tool.BUCKET) { push(); flood(p.x, p.y, col.getRGB()); repaint(); return; }
            if (tool == Tool.PICK) {
                Color c = new Color(img.getRGB(p.x, p.y));
                if (right) bg = c; else fg = c;
                indicator.repaint(); return;
            }
            if (tool == Tool.TEXT) {
                String s = JOptionPane.showInputDialog(Paint.this, t("textq"));
                if (s != null && !s.isEmpty()) {
                    push(); Graphics2D g = img.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.setColor(col); g.setFont(new Font("SansSerif", Font.PLAIN, 10 + sizeSl.getValue() * 2));
                    g.drawString(s, p.x, p.y + g.getFontMetrics().getAscent()); g.dispose(); repaint();
                }
                return;
            }
            push(); p0 = p1 = last = p;
            if (free()) seg(p, p);
            repaint();
        }
        void drag(MouseEvent e) {
            Point p = toImg(e.getPoint()); moved(p);
            if (dragOff != null) { fx = p.x - dragOff.x; fy = p.y - dragOff.y; repaint(); return; }
            if (selecting) { p1 = p; repaint(); return; }
            if (p0 == null) return;
            p1 = p;
            if (free()) { seg(last, p); last = p; }
            repaint();
        }
        void release(MouseEvent e) {
            Point p = toImg(e.getPoint());
            if (dragOff != null) { dragOff = null; return; }
            if (selecting) {
                selecting = false;
                Rectangle r = norm(p0, p).intersection(new Rectangle(0, 0, img.getWidth(), img.getHeight()));
                sel = r.isEmpty() ? null : r; p0 = null; repaint(); return;
            }
            if (p0 == null) return;
            if (!free()) { Graphics2D g = img.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); shape(g, p0, p); g.dispose(); }
            p0 = null; repaint();
        }

        // --- dessin 2D ---
        void seg(Point a, Point b) {
            Graphics2D g = img.createGraphics();
            boolean pen = tool == Tool.PENCIL, er = tool == Tool.ERASER;
            float w = pen ? 1 : er ? sizeSl.getValue() * 3 : sizeSl.getValue();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, pen ? RenderingHints.VALUE_ANTIALIAS_OFF : RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(er ? bg : col); g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (a.equals(b)) { if (pen) { if (inside(a)) img.setRGB(a.x, a.y, col.getRGB()); } else g.fill(new Ellipse2D.Float(a.x - w / 2, a.y - w / 2, w, w)); }
            else g.drawLine(a.x, a.y, b.x, b.y);
            g.dispose();
        }
        void shape(Graphics2D g, Point a, Point b) {
            Rectangle r = norm(a, b);
            Shape s;
            switch (tool) {
                case LINE: s = new Line2D.Float(a, b); break;
                case RECT: s = new Rectangle(r); break;
                case OVAL: s = new Ellipse2D.Float(r.x, r.y, r.width, r.height); break;
                default: s = new RoundRectangle2D.Float(r.x, r.y, r.width, r.height, 24, 24);
            }
            if (fillBox.isSelected() && tool != Tool.LINE) { g.setColor(alt); g.fill(s); }
            g.setColor(col); g.setStroke(new BasicStroke(sizeSl.getValue(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)); g.draw(s);
        }
        void flood(int x, int y, int nc) {
            int w = img.getWidth(), h = img.getHeight(), oc = img.getRGB(x, y);
            if (oc == nc) return;
            ArrayDeque<int[]> st = new ArrayDeque<>(); st.push(new int[] { x, y });
            while (!st.isEmpty()) {
                int[] q = st.pop(); int py = q[1], l = q[0], r = q[0];
                if (l < 0 || py < 0 || l >= w || py >= h || img.getRGB(l, py) != oc) continue;
                while (l > 0 && img.getRGB(l - 1, py) == oc) l--;
                while (r < w - 1 && img.getRGB(r + 1, py) == oc) r++;
                for (int i = l; i <= r; i++) {
                    img.setRGB(i, py, nc);
                    if (py > 0 && img.getRGB(i, py - 1) == oc) st.push(new int[] { i, py - 1 });
                    if (py < h - 1 && img.getRGB(i, py + 1) == oc) st.push(new int[] { i, py + 1 });
                }
            }
        }
        void dash(Graphics2D g, Rectangle r) {
            g.setColor(new Color(0x1E5BFF));
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 4, 4 }, 0));
            g.drawRect(r.x, r.y, r.width, r.height);
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create(); g.scale(zoom, zoom);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, zoom >= 2 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, null);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (p0 != null && p1 != null && !selecting && !free() && tool != Tool.SELECT) shape(g, p0, p1);
            if (fl != null) { g.drawImage(fl, fx, fy, null); dash(g, new Rectangle(fx, fy, fl.getWidth(), fl.getHeight())); }
            else if (selecting && p0 != null) dash(g, norm(p0, p1));
            else if (sel != null) dash(g, sel);
            g.dispose();
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new Paint().setVisible(true));
    }
}