package municipios.algoritmo;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UnionFindTest {

    @Test
    void alInicioCadaElementoEstaSolo() {
        UnionFind uf = new UnionFind(5);
        assertEquals(5, uf.getComponentes());
        assertFalse(uf.conectados(0, 1));
        assertEquals(3, uf.find(3));
    }

    @Test
    void unirConectaYReduceComponentes() {
        UnionFind uf = new UnionFind(4);
        assertTrue(uf.union(0, 1));
        assertTrue(uf.conectados(0, 1));
        assertEquals(3, uf.getComponentes());
    }

    @Test
    void unirDeNuevoDevuelveFalse() {
        UnionFind uf = new UnionFind(3);
        assertTrue(uf.union(0, 1));
        assertFalse(uf.union(0, 1));
        assertFalse(uf.union(1, 0));
        assertEquals(2, uf.getComponentes());
    }

    @Test
    void laConexionEsTransitiva() {
        UnionFind uf = new UnionFind(4);
        uf.union(0, 1);
        uf.union(1, 2);
        assertTrue(uf.conectados(0, 2));
        assertFalse(uf.conectados(0, 3));
        // cerrar el triángulo 0-1-2 formaría un ciclo
        assertFalse(uf.union(0, 2));
    }

    @Test
    void unirTodosEnCadenaDejaUnSoloComponente() {
        UnionFind uf = new UnionFind(20);
        for (int i = 0; i < 19; i++) {
            assertTrue(uf.union(i, i + 1));
        }
        assertEquals(1, uf.getComponentes());
        assertTrue(uf.conectados(0, 19));
    }

    @Test
    void unSoloElementoFunciona() {
        UnionFind uf = new UnionFind(1);
        assertEquals(1, uf.getComponentes());
        assertEquals(0, uf.find(0));
    }

    @Test
    void indicesInvalidosLanzanExcepcion() {
        UnionFind uf = new UnionFind(3);
        assertThrows(IllegalArgumentException.class, () -> uf.find(-1));
        assertThrows(IllegalArgumentException.class, () -> uf.find(3));
        assertThrows(IllegalArgumentException.class, () -> uf.union(0, 9));
        assertThrows(IllegalArgumentException.class, () -> new UnionFind(-1));
    }
}