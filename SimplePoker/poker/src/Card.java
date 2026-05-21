public class Card {
    static final String[] RANKS = {"2","3","4","5","6","7","8","9","10","J","Q","K","A"};
    static final String[] SUITS = {"♠","♥","♦","♣"};

    int rank; // 0–12
    int suit; // 0–3
    boolean faceUp;

    Card(int rank, int suit) {
        this.rank = rank;
        this.suit = suit;
        this.faceUp = false;
    }

    boolean isRed() { return suit == 1 || suit == 2; }

    @Override
    public String toString() { return RANKS[rank] + SUITS[suit]; }
}
