import java.util.*;

public class Deck {
    private final List<Card> cards = new ArrayList<>();

    Deck() { reset(); }

    void reset() {
        cards.clear();
        for (int s = 0; s < 4; s++)
            for (int r = 0; r < 13; r++)
                cards.add(new Card(r, s));
        Collections.shuffle(cards);
    }

    Card deal() { return cards.remove(cards.size() - 1); }
}
