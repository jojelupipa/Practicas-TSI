package practica_busqueda;

import core.game.Observation;
import core.game.StateObservation;
import ontology.Types;
import tools.Pair;
import tools.Vector2d;

import java.util.ArrayList;
import java.util.Vector;

/**
 * Created by dperez on 14/01/16.
 */

public class PathFinder {

    private AStar astar;
    public StateObservation state;

    public boolean VERBOSE = false;

    //All types are obstacles except the ones included in this array
    public ArrayList<Integer> obstacleItypes;

    public ArrayList<Observation> grid[][];


    private static int[] x_arrNeig = null;
    private static int[] y_arrNeig = null;

    public PathFinder(ArrayList<Integer> obstacleItypes)
    {
        this.obstacleItypes = obstacleItypes;
    }

    public void run(StateObservation stateObs)
    {
        this.state = stateObs;
        this.grid = stateObs.getObservationGrid();
        this.astar = new AStar(this);

        init();
        //runAll();

        if(VERBOSE)
        {
            for(Integer pathId : astar.pathCache.keySet())
            {
                ArrayList<Node> nodes = astar.pathCache.get(pathId);
                astar.printPath(pathId, nodes);
            }
        }
    }

    private void init()
    {
        if(x_arrNeig == null)
        {
            //TODO: This is a bit of a hack, it wouldn't work with other (new) action sets.
            ArrayList<Types.ACTIONS> actions = this.state.getAvailableActions();
            if(actions.size() == 3)
            {
                //left, right
                x_arrNeig = new int[]{-1, 1};
                y_arrNeig = new int[]{0,  0};
            }else
            {
                //up, down, left, right
                x_arrNeig = new int[]{0,    0,    -1,    1};
                y_arrNeig = new int[]{-1,   1,     0,    0};
            }
        }
    }

    private void runAll()
    {
        for(int i = 0; i < grid.length; ++i)
        {
            for(int j = 0; j < grid[i].length; ++j)
            {
                boolean obstacleCell = isObstacle(i,j);
                if(!obstacleCell)
                {
                    if(VERBOSE) System.out.println("Running from (" + i +  "," + j + ")");
                    runAll(i,j);
                }

            }
        }
    }

    public ArrayList<Node> getPath(Vector2d start, Vector2d end)
    {
        return astar.getPath(new Node(start), new Node(end));
    }

    public ArrayList<Node> findPath(Vector2d start, Vector2d end) {
        return astar.findPath(new Node(start), new Node(end));
    }

    private void runAll(int i, int j) {
        Node start = new Node(new Vector2d(i,j));
        Node goal = null; //To get all routes.

        astar.findPath(start, goal);
    }



    public boolean isObstacle(int row, int col)
    {
        if(row<0 || row>=grid.length) return true;
        if(col<0 || col>=grid[row].length) return true;

        for(Observation obs : grid[row][col])
        {
            if(obstacleItypes.contains(obs.itype))
                return true;
        }

        return false;

    }

    public ArrayList<Node> getNeighbours(Node node) {
        ArrayList<Node> neighbours = new ArrayList<Node>();
        int x = (int) (node.position.x);
        int y = (int) (node.position.y);

        for(int i = 0; i < x_arrNeig.length; ++i)
        {
            if(!isObstacle(x+x_arrNeig[i], y+y_arrNeig[i]))
            {
                neighbours.add(new Node(new Vector2d(x+x_arrNeig[i], y+y_arrNeig[i])));
            }
        }

        return neighbours;
    }

    // --------------------------------------------------------------------
    // Funciones auxiliares, modificación de A*
    // --------------------------------------------------------------------

    public boolean enemigosEnArea(Node n) {
        final int LIMITE = 5;
        int[] x_arrNeig = new int[]{0,    0,    -1,    1};
        int[] y_arrNeig = new int[]{-1,   1,     0,    0};
        ArrayList<Pair<Vector2d, Integer>> abiertos = new ArrayList<>();
        ArrayList<Vector2d> cerrados = new ArrayList<>();
        abiertos.add(new Pair<>(n.position, 0));
        while (!abiertos.isEmpty()) {
            Pair<Vector2d, Integer> actual = abiertos.get(0);
            cerrados.add(actual.first);
            abiertos.remove(0);
            for (int i = 0; i < x_arrNeig.length; ++i) {
                Pair<Vector2d, Integer> nuevo = new Pair<>(new Vector2d(actual.first.x + x_arrNeig[i], actual.first.y + y_arrNeig[i]), actual.second + 1);
                if (actual.second < LIMITE && estaExcavado(nuevo.first) && !contiene(abiertos, nuevo) && !cerrados.contains(nuevo.first)) {
                    abiertos.add(nuevo);
                    if (esEnemigo(nuevo.first))
                        return true;
                }
            }
        }
        return false;
    }

    private boolean esAlgo(int x, int y, int iType) {
        if (x < 0 || x >= grid.length) return false;
        if (y < 0 || y >= grid[x].length) return false;

        for (Observation obs : grid[x][y]) {
            if (obs.itype == iType)
                return true;
        }
        return false;
    }

    private boolean esPiedra(int x, int y) {
        return esAlgo(x, y, 7);
    }

    private boolean esEnemigo(int x, int y) {
        return esAlgo(x, y, 10) || esAlgo(x, y, 11);
    }

    private boolean esEnemigo(Vector2d v) {
        return esEnemigo((int) v.x, (int) v.y);
    }

    public boolean piedraEncima(Node casilla) {
        return (esPiedra((int) casilla.position.x, (int) casilla.position.y - 1));
    }

    private boolean estaExcavado(int x, int y) {
        return !esAlgo(x, y, 4) && !esAlgo(x, y, 0) && !esAlgo(x, y, 7);
    }

    private boolean estaExcavado(Vector2d v) {
        return estaExcavado((int) v.x, (int) v.y);
    }

    private boolean contiene(ArrayList<Pair<Vector2d, Integer>> abiertos, Pair<Vector2d, Integer> nuevo) {
        boolean res =  false;
        for (int i = 0; i < abiertos.size() && !res; ++i)
            if (abiertos.get(i).first.equals(nuevo.first))
                res = true;
        return res;
    }
}
