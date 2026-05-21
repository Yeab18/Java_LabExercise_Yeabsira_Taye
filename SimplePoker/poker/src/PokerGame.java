import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class PokerGame extends JFrame {

    // ── Game state ────────────────────────────────────────
    Deck deck = new Deck();
    List<Card> playerHand = new ArrayList<>();
    List<Card> cpuHand    = new ArrayList<>();
    List<Card> community  = new ArrayList<>();
    int playerChips = 1000, cpuChips = 1000;
    int pot = 0, playerBet = 0, cpuBet = 0;
    int stage = 0; // 0=deal, 1=preflop, 2=flop, 3=turn, 4=river, 5=showdown
    boolean playerFolded = false;

    // ── UI components ─────────────────────────────────────
    JPanel cpuPanel, communityPanel, playerPanel;
    JLabel potLabel, messageLabel, playerChipsLabel, cpuChipsLabel, handLabel;
    JButton dealBtn, callBtn, raiseBtn, foldBtn, checkBtn;
    JSpinner raiseSpinner;

    public PokerGame() {
        super("♠ Poker ♥");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(780, 620);
        setLocationRelativeTo(null);
        setResizable(false);
        buildUI();
        setVisible(true);
        message("Click Deal to start!");
    }

    // ── UI Builder ────────────────────────────────────────
    void buildUI() {
        JPanel root = new JPanel(new BorderLayout(8, 8)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setColor(new Color(20, 90, 40));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // ── CPU area ──
        JPanel cpuArea = new JPanel(new BorderLayout(4, 4));
        cpuArea.setOpaque(false);
        cpuChipsLabel = makeLabel("CPU: $1000", 14, new Color(180, 220, 255));
        cpuPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        cpuPanel.setOpaque(false);
        cpuArea.add(cpuChipsLabel, BorderLayout.NORTH);
        cpuArea.add(cpuPanel, BorderLayout.CENTER);

        // ── Community area ──
        JPanel midArea = new JPanel(new BorderLayout(6, 6));
        midArea.setOpaque(false);
        potLabel = makeLabel("Pot: $0", 18, new Color(255, 215, 0));
        communityPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        communityPanel.setOpaque(false);
        handLabel = makeLabel("", 13, new Color(255, 200, 80));
        midArea.add(potLabel, BorderLayout.NORTH);
        midArea.add(communityPanel, BorderLayout.CENTER);
        midArea.add(handLabel, BorderLayout.SOUTH);

        // ── Player area ──
        JPanel playerArea = new JPanel(new BorderLayout(4, 4));
        playerArea.setOpaque(false);
        playerChipsLabel = makeLabel("You: $1000", 14, new Color(255, 220, 120));
        playerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        playerPanel.setOpaque(false);
        playerArea.add(playerPanel, BorderLayout.CENTER);
        playerArea.add(playerChipsLabel, BorderLayout.SOUTH);

        // ── Message bar ──
        messageLabel = makeLabel("", 14, Color.WHITE);
        messageLabel.setHorizontalAlignment(SwingConstants.CENTER);
        messageLabel.setBorder(new EmptyBorder(4, 0, 4, 0));

        // ── Controls ──
        JPanel controls = buildControls();

        // ── Layout ──
        JPanel center = new JPanel(new GridLayout(3, 1, 0, 8));
        center.setOpaque(false);
        center.add(cpuArea);
        center.add(midArea);
        center.add(playerArea);

        root.add(center, BorderLayout.CENTER);
        root.add(messageLabel, BorderLayout.NORTH);
        root.add(controls, BorderLayout.SOUTH);
        setContentPane(root);
    }

    JPanel buildControls() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 6));
        p.setOpaque(false);

        dealBtn  = btn("🃏 Deal",  new Color(160, 120, 0),  e -> deal());
        callBtn  = btn("Call",    new Color(30, 110, 60),   e -> playerCall());
        checkBtn = btn("Check",   new Color(30, 80, 140),   e -> playerCheck());
        raiseBtn = btn("Raise",   new Color(130, 70, 0),    e -> playerRaise());
        foldBtn  = btn("Fold",    new Color(140, 30, 30),   e -> playerFold());

        raiseSpinner = new JSpinner(new SpinnerNumberModel(50, 10, 1000, 10));
        raiseSpinner.setPreferredSize(new Dimension(70, 32));
        JLabel raiseLabel = makeLabel("$", 13, Color.WHITE);

        p.add(dealBtn);
        p.add(new JSeparator(SwingConstants.VERTICAL));
        p.add(foldBtn);
        p.add(checkBtn);
        p.add(callBtn);
        p.add(raiseLabel);
        p.add(raiseSpinner);
        p.add(raiseBtn);

        setActions(false);
        return p;
    }

    // ── Game logic ────────────────────────────────────────
    void deal() {
        deck.reset();
        playerHand.clear(); cpuHand.clear(); community.clear();
        pot = 0; playerBet = 0; cpuBet = 0;
        playerFolded = false;
        stage = 1;

        // Deal 2 cards each
        playerHand.add(faceUp(deck.deal()));
        playerHand.add(faceUp(deck.deal()));
        cpuHand.add(deck.deal()); // face down
        cpuHand.add(deck.deal());

        // Blinds: player = small $10, cpu = big $20
        int sb = Math.min(10, playerChips);
        int bb = Math.min(20, cpuChips);
        playerChips -= sb; playerBet = sb; pot += sb;
        cpuChips    -= bb; cpuBet    = bb; pot += bb;

        refresh();
        message("Small blind $" + sb + " | Big blind $" + bb + " — Call, Raise, or Fold?");
        setActions(true);
        dealBtn.setEnabled(false);
    }

    void playerCall() {
        int needed = cpuBet - playerBet;
        int actual = Math.min(needed, playerChips);
        playerChips -= actual; playerBet += actual; pot += actual;
        message("You called $" + actual);
        refresh();
        nextStage();
    }

    void playerCheck() {
        if (cpuBet > playerBet) { message("Can't check — must call or fold!"); return; }
        message("You checked.");
        refresh();
        nextStage();
    }

    void playerRaise() {
        int amount = (int) raiseSpinner.getValue();
        int call   = cpuBet - playerBet;
        int total  = Math.min(call + amount, playerChips);
        playerChips -= total; playerBet += total; pot += total;
        if (playerBet > cpuBet) cpuBet = playerBet; // cpu must at least match
        message("You raised $" + amount + ". CPU calls.");
        // Simple CPU response: always call
        int cpuNeeded = Math.min(playerBet - cpuBet, cpuChips);
        cpuChips -= cpuNeeded; cpuBet += cpuNeeded; pot += cpuNeeded;
        refresh();
        nextStage();
    }

    void playerFold() {
        playerFolded = true;
        cpuChips += pot;
        message("You folded. CPU wins $" + pot + "!");
        endHand();
    }

    void nextStage() {
        stage++;
        switch (stage) {
            case 2 -> { // Flop: deal 3
                community.add(faceUp(deck.deal()));
                community.add(faceUp(deck.deal()));
                community.add(faceUp(deck.deal()));
                message("The Flop! Call, Raise, or Fold?");
            }
            case 3 -> { // Turn
                community.add(faceUp(deck.deal()));
                message("The Turn! Call, Raise, or Fold?");
            }
            case 4 -> { // River
                community.add(faceUp(deck.deal()));
                message("The River! Final bets...");
            }
            case 5 -> { // Showdown
                showdown();
                return;
            }
        }
        // Update check button availability
        checkBtn.setEnabled(cpuBet <= playerBet);
        refresh();
    }

    void showdown() {
        // Reveal CPU cards
        cpuHand.forEach(c -> c.faceUp = true);
        refresh();

        int pScore = HandEvaluator.score(playerHand, community);
        int cScore = HandEvaluator.score(cpuHand, community);
        String pName = HandEvaluator.name(pScore);
        String cName = HandEvaluator.name(cScore);

        if (pScore > cScore) {
            playerChips += pot;
            message("You win! " + pName + " beats CPU's " + cName + "! 🏆 +$" + pot);
        } else if (cScore > pScore) {
            cpuChips += pot;
            message("CPU wins with " + cName + " over your " + pName + ". 😞");
        } else {
            int half = pot / 2;
            playerChips += half; cpuChips += half;
            message("Split pot! Both had " + pName + ". $" + half + " each.");
        }

        handLabel.setText("Your hand: " + pName + " | CPU: " + cName);
        endHand();
    }

    void endHand() {
        setActions(false);
        dealBtn.setEnabled(true);
        if (playerChips <= 0) { message("You're broke! Game over. 💸"); dealBtn.setEnabled(false); }
        if (cpuChips    <= 0) { message("CPU is broke! You win the game! 🎉"); dealBtn.setEnabled(false); }
        refresh();
    }

    // ── Rendering ─────────────────────────────────────────
    void refresh() {
        potLabel.setText("Pot: $" + pot);
        playerChipsLabel.setText("You: $" + playerChips + "  (bet: $" + playerBet + ")");
        cpuChipsLabel.setText("CPU: $" + cpuChips + "  (bet: $" + cpuBet + ")");

        cpuPanel.removeAll();
        for (Card c : cpuHand) cpuPanel.add(cardWidget(c));

        communityPanel.removeAll();
        for (Card c : community) communityPanel.add(cardWidget(c));
        // Empty slots
        for (int i = community.size(); i < 5; i++) communityPanel.add(emptyCard());

        playerPanel.removeAll();
        for (Card c : playerHand) playerPanel.add(cardWidget(c));

        // Live hand rank for player
        if (!playerHand.isEmpty() && community.size() >= 3) {
            int s = HandEvaluator.score(playerHand, community);
            handLabel.setText("Your current hand: " + HandEvaluator.name(s));
        } else if (!playerHand.isEmpty()) {
            handLabel.setText("");
        }

        cpuPanel.revalidate(); cpuPanel.repaint();
        communityPanel.revalidate(); communityPanel.repaint();
        playerPanel.revalidate(); playerPanel.repaint();
    }

    JPanel cardWidget(Card card) {
        return new JPanel() {
            { setPreferredSize(new Dimension(65, 95)); setOpaque(false); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D r = new RoundRectangle2D.Float(1,1,62,92,10,10);

                if (!card.faceUp) {
                    g2.setColor(new Color(30, 60, 130));
                    g2.fill(r);
                    g2.setColor(new Color(255, 215, 0, 100));
                    g2.setStroke(new BasicStroke(1f));
                    for (int i=6; i<90; i+=10) g2.drawLine(0,i,64,i-8);
                    g2.setColor(new Color(255,215,0));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.draw(r);
                } else {
                    // Shadow
                    g2.setColor(new Color(0,0,0,40));
                    g2.fillRoundRect(3,4,62,92,10,10);
                    // Face
                    g2.setColor(Color.WHITE);
                    g2.fill(r);
                    Color ink = card.isRed() ? new Color(200,30,30) : new Color(15,15,15);
                    // Rank + suit top-left
                    g2.setFont(new Font("Georgia", Font.BOLD, 15));
                    g2.setColor(ink);
                    g2.drawString(Card.RANKS[card.rank], 4, 17);
                    g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 13));
                    g2.drawString(Card.SUITS[card.suit], 4, 30);
                    // Big center suit
                    g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 32));
                    FontMetrics fm = g2.getFontMetrics();
                    String sym = Card.SUITS[card.suit];
                    g2.drawString(sym, (64-fm.stringWidth(sym))/2, 64);
                    // Border
                    g2.setColor(new Color(180,180,180));
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(r);
                }
                g2.dispose();
            }
        };
    }

    JPanel emptyCard() {
        return new JPanel() {
            { setPreferredSize(new Dimension(65, 95)); setOpaque(false); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255,255,255,20));
                g2.fill(new RoundRectangle2D.Float(1,1,62,92,10,10));
                g2.setColor(new Color(255,255,255,40));
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{4,4}, 0));
                g2.draw(new RoundRectangle2D.Float(1,1,62,92,10,10));
                g2.dispose();
            }
        };
    }

    // ── Helpers ───────────────────────────────────────────
    Card faceUp(Card c) { c.faceUp = true; return c; }
    void message(String s) { messageLabel.setText(s); }
    void setActions(boolean on) {
        foldBtn.setEnabled(on); checkBtn.setEnabled(on);
        callBtn.setEnabled(on); raiseBtn.setEnabled(on);
    }

    JLabel makeLabel(String text, int size, Color color) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(new Font("Georgia", Font.BOLD, size));
        l.setForeground(color);
        return l;
    }

    JButton btn(String label, Color bg, ActionListener action) {
        JButton b = new JButton(label) {
            boolean hov = false;
            { addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e){hov=true;repaint();}
                public void mouseExited(MouseEvent e){hov=false;repaint();}
            }); }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? (hov ? bg.brighter() : bg) : new Color(70,70,70));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                g2.setColor(Color.WHITE);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),(getWidth()-fm.stringWidth(getText()))/2,(getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        b.setFont(new Font("Arial", Font.BOLD, 13));
        b.setPreferredSize(new Dimension(90, 34));
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(action);
        return b;
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(PokerGame::new);
    }
}
