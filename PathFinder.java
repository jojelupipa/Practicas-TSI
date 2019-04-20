package practica_busqueda;

import core.game.Observation;
import core.game.StateObservation;
import ontology.Types;
import tools.Pair;
import tools.Vector2d;

import java.util.ArrayList;

/**
 * Created by dperez on 14/01/16.
 */

class Pareja {

    private Node nodo;
    private int deep;

    public boolean equals(Pareja obj) {
        return this.nodo == obj.nodo;
    }

    public Pareja(Node nodo, int n) {
        this.nodo = nodo;
        this.deep = n;
    }

    public Pareja(Pair<Node, Integer> pair) {
        this.nodo = pair.first;
        this.deep = pair.second;
    }

    public Node first() {
        return nodo;
    }

    public int second() {
        return deep;
    }
}


public class PathFinder {

    public AStar astar;
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

    public boolean rockAbove(Node casilla) {
        return (isRock((int) casilla.position.x, (int) casilla.position.y - 1));
    }


    public boolean enemyNear(Node casilla) {
        int nEnemigos = 0;

        int x = (int) (casilla.position.x);
        int y = (int) (casilla.position.y);

        for (int i = 0; i < x_arrNeig.length; ++i) {
            if (isEnemy(x + x_arrNeig[i], y + y_arrNeig[i]))
                return true;
        }
        return false;
    }

    private boolean isRock(int x, int y) {
        if (x < 0 || x >= grid.length) return false;
        if (y < 0 || y >= grid[x].length) return false;

        for (Observation obs : grid[x][y]) {
            if (obs.itype == 7)
                return true;
        }
        return false;
    }

    private boolean isEnemy(int x, int y) {

        if (x < 0 || x >= grid.length) return false;
        if (y < 0 || y >= grid[x].length) return false;

        for (Observation obs : grid[x][y]) {
            if (obs.itype == 10 || obs.itype == 11)
                return true;
        }
        return false;
    }


    private boolean withinBoundries(int x, int y) {
        return x >= 0 && x < grid.length && y >= 0 && y < grid[x].length;
    }


    public boolean areaEnemies(Node nodo) {
        final int LIMITE = 5;

        int x = (int) nodo.position.x;
        int y = (int) nodo.position.y;

        int[] x_arrNeig = new int[]{0,    0,    -1,    1};
        int[] y_arrNeig = new int[]{-1,   1,     0,    0};

        ArrayList<Pareja> abiertos = new ArrayList<>();
        ArrayList<Node> cerrados = new ArrayList<>();
        Node nOrigen = new Node(new Vector2d(x, y));
        abiertos.add(new Pareja(nOrigen, 0));
        while (!abiertos.isEmpty()) {
            Pareja pareja = abiertos.get(0);
            Node actual = pareja.first();
            int deep = pareja.second();
            cerrados.add(actual);
            abiertos.remove(0);
            int xa = (int) actual.position.x;
            int ya = (int) actual.position.y;
            for (int i = 0; i < x_arrNeig.length; ++i) {
                int xn = xa + x_arrNeig[i];
                int yn = ya + y_arrNeig[i];
                if (deep < LIMITE && withinBoundries(xn, yn) && isDug(xn, yn) && !abiertos.contains(new Pareja(new Node(new Vector2d(xn, yn)), deep + 1)) && !cerrados.contains(new Node(new Vector2d(xn, yn)))) {
                    abiertos.add(new Pareja(new Pair(new Node(new Vector2d(xn, yn)), deep + 1)));
                    if (isEnemy(xn, yn)) {
                        //System.out.println("Enemigo en (" + xn + ", " + yn + ")");
                        return true;
                    }
                }
            }
        }
        return false;
    }


    private boolean isDug(int x, int y) {
        if (x < 0 || x >= grid.length) return false;
        if (y < 0 || y >= grid[x].length) return false;

        boolean excavado = false;

        for (Observation obs : grid[x][y]) {
            excavado = excavado || obs.itype == 4 || obs.itype == 0 || obs.itype == 7;
        }
        return !excavado;
    }




}
