import java.util.*;

public class TestFusion {

    private static long packXZ(int x, int z) { return ((long)x << 32) | (z & 0xFFFFFFFFL); }

    private static boolean isBoundaryCell(Set<Long> filled, int x, int z) {
        if (!filled.contains(packXZ(x, z))) return false;
        return !filled.contains(packXZ(x + 1, z))
                || !filled.contains(packXZ(x - 1, z))
                || !filled.contains(packXZ(x, z + 1))
                || !filled.contains(packXZ(x, z - 1));
    }

    private static List<int[]> traceOuterBoundary(Set<Long> filled, int maxSteps) {
        if (filled == null || filled.isEmpty()) return null;

        long start = 0;
        boolean found = false;
        int startX = 0, startZ = 0;
        for (long k : filled) {
            int x = (int) (k >> 32);
            int z = (int) k;
            if (isBoundaryCell(filled, x, z)) {
                if (!found || x < startX || (x == startX && z < startZ)) {
                    start = k; startX = x; startZ = z; found = true;
                }
            }
        }
        if (!found) return null;

        int[][] dirs = new int[][]{{1,0},{0,1},{-1,0},{0,-1}};
        int dir = 0;

        List<int[]> loop = new ArrayList<>();
        int cx = startX; int cz = startZ;
        loop.add(new int[]{cx, cz});

        int steps = 0;
        while (steps++ < maxSteps) {
            boolean moved = false;
            for (int i = 0; i < 4; i++) {
                int ndir = (dir + 3 + i) % 4;
                int nx = cx + dirs[ndir][0];
                int nz = cz + dirs[ndir][1];
                if (filled.contains(packXZ(nx, nz)) && isBoundaryCell(filled, nx, nz)) {
                    cx = nx; cz = nz; dir = ndir; loop.add(new int[]{cx, cz}); moved = true; break;
                }
            }
            if (!moved) return null;
            if (cx == startX && cz == startZ && loop.size() > 4) return loop;
        }
        return null;
    }

    private static Set<Long> rect(int minX, int maxX, int minZ, int maxZ) {
        Set<Long> s = new HashSet<>();
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) s.add(packXZ(x,z));
        return s;
    }

    private static void runCase(String name, Set<Long> a, Set<Long> b) {
        Set<Long> union = new HashSet<>(a);
        union.addAll(b);
        System.out.println("Case: " + name);
        System.out.println(" union size=" + union.size());
        // close diagonal gaps like the plugin does
        boolean changed = true;
        int safety = 0;
        while (changed && safety++ < 10000) {
            changed = false;
            Set<Long> snapshot = new HashSet<>(union);
            for (long k : snapshot) {
                int x = (int) (k >> 32);
                int z = (int) k;
                for (int dx : new int[]{1, -1}) {
                    for (int dz : new int[]{1, -1}) {
                        long diag = packXZ(x + dx, z + dz);
                        long orth1 = packXZ(x + dx, z);
                        long orth2 = packXZ(x, z + dz);
                        if (union.contains(diag)) {
                            if (!union.contains(orth1)) { union.add(orth1); changed = true; }
                            if (!union.contains(orth2)) { union.add(orth2); changed = true; }
                        }
                    }
                }
            }
            if (!changed) break;
        }

        List<int[]> loop = traceOuterBoundary(union, 10000);
        if (loop == null) System.out.println(" boundary: null");
        else {
            System.out.println(" boundary size=" + loop.size());
            for (int[] p : loop) System.out.print("("+p[0]+","+p[1]+") ");
            System.out.println();
        }
        System.out.println();
    }

    public static void main(String[] args) {
        Set<Long> r1 = rect(0,2,0,2); // 3x3
        Set<Long> r2_adj = rect(3,5,0,2); // adjacent at x=3 next to x=2
        Set<Long> r2_diag = rect(3,5,3,5); // diagonal
        Set<Long> r2_far = rect(5,7,0,2); // separated

        runCase("adjacent", r1, r2_adj);
        runCase("diagonal", r1, r2_diag);
        runCase("separated", r1, r2_far);

        // also test thin connection: single bridge
        Set<Long> bridge = rect(3,3,0,0); // single cell between
        runCase("bridge", r1, bridge);
    }
}
