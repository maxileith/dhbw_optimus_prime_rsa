package optimus.prime.rsa.main;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class Utils {
    public static Queue<Integer> getIndicesToDo(int numberOfPrimes, int sliceSize) {
        Queue<Integer> indices = new LinkedList<>();
        int lastIndex = numberOfPrimes - 1;
        for (int i = 0; i <= lastIndex; i += sliceSize) {
            indices.add(i);
        }
        return indices;
    }
}
