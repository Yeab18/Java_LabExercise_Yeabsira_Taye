import java.util.*;

public class HandEvaluator {
    static final String[] HAND_NAMES = {
        "High Card","One Pair","Two Pair","Three of a Kind",
        "Straight","Flush","Full House","Four of a Kind","Straight Flush","Royal Flush"
    };

    // Returns score 0–9 (higher = better)
    static int score(List<Card> hand, List<Card> community) {
        List<Card> all = new ArrayList<>(hand);
        all.addAll(community);
        int best = 0;
        // Try all 5-card combos from the 7 cards
        for (int[] combo : combos(all.size(), 5)) {
            List<Card> five = new ArrayList<>();
            for (int i : combo) five.add(all.get(i));
            best = Math.max(best, rank5(five));
        }
        return best;
    }

    static String name(int score) { return HAND_NAMES[score]; }

    private static int rank5(List<Card> c) {
        int[] ranks = c.stream().mapToInt(x -> x.rank).sorted().toArray();
        boolean flush = c.stream().mapToInt(x -> x.suit).distinct().count() == 1;
        boolean straight = (ranks[4]-ranks[0]==4 && new int[]{ranks[0],ranks[1],ranks[2],ranks[3],ranks[4]}.length==5
                && countDistinct(ranks)==5) || isWheel(ranks);
        Map<Integer,Integer> freq = new HashMap<>();
        for (int r : ranks) freq.merge(r, 1, Integer::sum);
        List<Integer> counts = new ArrayList<>(freq.values());
        Collections.sort(counts, Collections.reverseOrder());

        if (flush && straight) return ranks[4]==12 && ranks[0]==8 ? 9 : 8;
        if (counts.get(0)==4) return 7;
        if (counts.get(0)==3 && counts.get(1)==2) return 6;
        if (flush) return 5;
        if (straight) return 4;
        if (counts.get(0)==3) return 3;
        if (counts.get(0)==2 && counts.get(1)==2) return 2;
        if (counts.get(0)==2) return 1;
        return 0;
    }

    private static boolean isWheel(int[] r) {
        return r[0]==0&&r[1]==1&&r[2]==2&&r[3]==3&&r[4]==12;
    }

    private static int countDistinct(int[] arr) {
        return (int) Arrays.stream(arr).distinct().count();
    }

    private static List<int[]> combos(int n, int k) {
        List<int[]> result = new ArrayList<>();
        combine(n, k, 0, new int[k], 0, result);
        return result;
    }

    private static void combine(int n, int k, int start, int[] curr, int depth, List<int[]> result) {
        if (depth == k) { result.add(curr.clone()); return; }
        for (int i = start; i < n; i++) {
            curr[depth] = i;
            combine(n, k, i+1, curr, depth+1, result);
        }
    }
}
